import asyncio
import json
import logging
from fastapi import APIRouter, WebSocket, WebSocketDisconnect

router = APIRouter()

# WebSocket connections
clients = {}

@router.websocket("/ws/device")
async def device_websocket(websocket: WebSocket):
    await websocket.accept()
    client_id = None
    
    try:
        # Send device registration prompt
        await websocket.send_text(json.dumps({
            "type": "register",
            "message": "Device registration required. Send device_id."
        }))
        
        while True:
            data = await websocket.receive_text()
            
            try:
                message = json.loads(data)
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({"error": "Invalid JSON"}))
                continue
            
            # Handle device registration
            if message.get("type") == "register":
                client_id = message.get("device_id")
                if client_id not in clients:
                    clients[client_id] = websocket
                    await websocket.send_text(json.dumps({
                        "type": "registered",
                        "device_id": client_id,
                        "message": "Device registered successfully"
                    }))
                continue
            
            # Handle commands
            if client_id and message.get("type") == "command":
                command = message.get("command")
                params = message.get("params", {})
                
                # Process command
                response = process_command(command, params)
                
                # Send response back to device
                await websocket.send_text(json.dumps({
                    "type": "command_response",
                    "command": command,
                    "response": response
                }))
                
            # Handle data updates
            elif client_id and message.get("type") == "data":
                data_type = message.get("data_type")
                payload = message.get("payload")
                
                # Process data update
                await websocket.send_text(json.dumps({
                    "type": "data_received",
                    "data_type": data_type,
                    "payload": payload
                }))
    
    except WebSocketDisconnect:
        if client_id in clients:
            del clients[client_id]
        else:
            await websocket.send_text(json.dumps({"error": "Connection lost"}))
    
    except Exception as e:
        await websocket.send_text(json.dumps({"error": str(e)}))

def process_command(command, params):
    """Process command and return response"""
    logging.info(f"Processing command: {command}")
    # Implement command processing logic
    return {"status": "executed", "command": command}

@router.websocket("/ws/admin")
async def admin_websocket(websocket: WebSocket):
    """WebSocket endpoint for admin panel"""
    await websocket.accept()
    
    try:
        while True:
            data = await websocket.receive_text()
            
            try:
                message = json.loads(data)
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({"error": "Invalid JSON"}))
                continue
            
            # Handle admin commands
            if message.get("type") == "command":
                command = message.get("command")
                device_id = message.get("device_id")
                
                # Forward command to device
                if device_id in clients:
                    await clients[device_id].send_text(json.dumps({
                        "type": "command",
                        "command": command,
                        "params": message.get("params")
                    }))
                    await websocket.send_text(json.dumps({
                        "type": "sent",
                        "command": command,
                        "device_id": device_id
                    }))
    
    except WebSocketDisconnect:
        pass
    except Exception as e:
        await websocket.send_text(json.dumps({"error": str(e)}))

def broadcast_message(message: dict, device_id: str = None):
    """Broadcast message to specific device or all devices"""
    if device_id:
        if device_id in clients:
            clients[device_id].send_text(json.dumps(message))
    else:
        for client_id in clients:
            try:
                clients[device_id].send_text(json.dumps(message))
            except Exception as e:
                logging.error(f"Broadcast failed for {client_id}: {e}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(__name__, host="0.0.0.0", port=8000)
