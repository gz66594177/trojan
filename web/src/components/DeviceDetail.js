import React from 'react';

function DeviceDetail({ device }) {
  const [messages, setMessages] = React.useState([]);
  const [loading, setLoading] = React.useState(true);
  const [id, device_id, device_name, device_type, status, last_seen, created_at] = device;

  React.useEffect(() => {
    fetchMessages();
    const interval = setInterval(fetchMessages, 10000); // Real-time updates
    return () => clearInterval(interval);
  }, [device_id]);

  const fetchMessages = async () => {
    try {
      const response = await fetch(
        `http://localhost:8000/api/v1/device/${device_id}/messages?limit=50`
      );
      const data = await response.json();
      setMessages(data.messages || []);
      setLoading(false);
    } catch (err) {
      console.error('Failed to fetch messages:', err);
      setLoading(false);
    }
  };

  return (
    <div className="device-detail">
      <h2>设备详情: {device_name || device_id}</h2>
      <p>状态: <span className={`device-status status-${status}`}>{status}</span></p>
      
      <h3>消息记录</h3>
      {loading ? <div>加载中...</div> : (
          <div>
            {messages.length > 0 ? messages.map((msg, idx) => {
              const [msg_id, msg_type, payload, created_at] = msg;
              return (
                <div key={idx} className="log-entry">
                  <strong>{msg_type}</strong>
                  <p>{JSON.stringify(payload)}</p>
                  <small>{new Date(created_at).toLocaleString()}</small>
                </div>
              );
            }) : <p>暂无消息</p>}
          </div>
        )}
    </div>
  );
}

export default DeviceDetail;
