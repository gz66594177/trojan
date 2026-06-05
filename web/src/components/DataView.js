import React, { useState } from 'react';

function DataView({ devices }) {
  const [selectedDevice, setSelectedDevice] = useState('');
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);

  const fetchDeviceData = async () => {
    if (!selectedDevice) return;
    setLoading(true);
    try {
      const response = await fetch(
        `http://localhost:8000/api/v1/device/${selectedDevice}/messages?limit=100`
      );
      const data = await response.json();
      setMessages(data.messages || []);
    } catch (err) {
      console.error('Failed to fetch device data:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="data-view">
      <h2>数据查看</h2>
      <select
        value={selectedDevice}
        onChange={(e) => setSelectedDevice(e.target.value)}
        style={{ padding: '10px', fontSize: '16px' }}
      >
        <option value="">-- 选择设备 --</option>
        {devices.map(device => {
          const [id, device_id, device_name] = device;
          return <option key={device_id} value={device_id}>{device_name || device_id}</option>;
        })}
      </select>

      <button
        onClick={fetchDeviceData}
        style={{
          padding: '10px 20px',
          fontSize: '16px',
          background: '#00ff00',
          color: '#000',
          border: 'none',
          borderRadius: '5px',
          cursor: 'pointer',
          margin: '10px 0'
        }}
      >
        加载数据
      </button>

      <div style={{ marginTop: '20px' }}>
        {loading && <p>加载中...</p>}
        {messages.length > 0 ? (
          messages.map((msg, idx) => {
            const [msg_id, msg_type, payload, created_at] = msg;
            return (
              <div key={idx} className="log-entry" style={{ marginBottom: '10px' }}>
              <strong>{msg_type}</strong>
              <p>{JSON.stringify(payload)}</p>
              <small>{new Date(created_at).toLocaleString()}</small>
            </div>
            );
          })
        ) : (
          <p>请选择设备并加载数据</p>
        )}
      </div>
    </div>
  );
}

export default DataView;
