import React from 'react';

function DeviceList({ devices, onSelect, loading }) {
  if (loading) return <div>加载中...</div>;

  if (!devices || devices.length === 0) {
    return <div>暂无设备</div>;
  }

  return (
    <div className="device-list">
      {devices.map(device => {
        const [id, device_id, device_name, device_type, status, last_seen, created_at] = device;
        return (
          <div key={device_id} className="device-card" onClick={() => onSelect(device)}>
            <div className="device-info">
              <strong>{device_name || device_id}</strong>
              <span className={`device-status status-${status}`}>
                {status === 'active' ? '● 在线' : '● 离线'}
              </span>
            </div>
            <div className="device-info">
              <p>ID: {device_id}</p>
              <p>类型: {device_type || 'Android'}</p>
              <p>最后活动: {new Date(last_seen).toLocaleString()}</p>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default DeviceList;
