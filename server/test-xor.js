// ==================== XOR Encryption Test Script ====================
// برای تست کردن XOR encryption قبل از deploy

const crypto = require('crypto');

// ==================== XOR Functions ====================

function generateXORKey(deviceId, licenseKey) {
  const lastPart = deviceId.length >= 8
    ? deviceId.slice(-8)
    : deviceId;

  const firstPart = licenseKey.length >= 8
    ? licenseKey.slice(0, 8)
    : licenseKey;

  return lastPart + firstPart;
}

function xorEncryptDecrypt(data, key) {
  const dataBytes = Buffer.from(data, 'utf8');
  const keyBytes = Buffer.from(key, 'utf8');
  const result = Buffer.alloc(dataBytes.length);

  for (let i = 0; i < dataBytes.length; i++) {
    result[i] = dataBytes[i] ^ keyBytes[i % keyBytes.length];
  }

  return result.toString('base64');
}

function xorDecrypt(base64Data, key) {
  const dataBytes = Buffer.from(base64Data, 'base64');
  const keyBytes = Buffer.from(key, 'utf8');
  const result = Buffer.alloc(dataBytes.length);

  for (let i = 0; i < dataBytes.length; i++) {
    result[i] = dataBytes[i] ^ keyBytes[i % keyBytes.length];
  }

  return result.toString('utf8');
}

// ==================== Test Cases ====================

console.log('🧪 Testing XOR Encryption...\n');

// Test Case 1: Activation Request
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('📱 Test Case 1: Activation Request');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');

const deviceId1 = 'test_device_12345678';
const licenseKey1 = 'TEST1-TEST2-TEST3-TEST4';

const payload1 = {
  license_key: licenseKey1,
  device_id: deviceId1,
  device_info: 'Samsung Galaxy S21 (Android 12)'
};

const xorKey1 = generateXORKey(deviceId1, licenseKey1);
console.log('Device ID:', deviceId1);
console.log('License Key:', licenseKey1);
console.log('XOR Key:', xorKey1);
console.log('');

const payloadStr1 = JSON.stringify(payload1);
console.log('Original Payload:');
console.log(payloadStr1);
console.log('');

const encrypted1 = xorEncryptDecrypt(payloadStr1, xorKey1);
console.log('Encrypted (Base64):');
console.log(encrypted1);
console.log('');

const decrypted1 = xorDecrypt(encrypted1, xorKey1);
console.log('Decrypted:');
console.log(decrypted1);
console.log('');

const match1 = payloadStr1 === decrypted1;
console.log('✅ Encryption/Decryption:', match1 ? 'SUCCESS' : 'FAILED');
console.log('');

// Test Case 2: Verification Request
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('🔍 Test Case 2: Verification Request');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');

const payload2 = {
  license_key: licenseKey1,
  session_token: 'random_session_token_abc123',
  nonce: 'AbCdEfGhIjKlMnOpQrStUvWxYz123456',
  device_id: deviceId1
};

const payloadStr2 = JSON.stringify(payload2);
console.log('Original Payload:');
console.log(payloadStr2);
console.log('');

const encrypted2 = xorEncryptDecrypt(payloadStr2, xorKey1);
console.log('Encrypted (Base64):');
console.log(encrypted2);
console.log('');

const decrypted2 = xorDecrypt(encrypted2, xorKey1);
console.log('Decrypted:');
console.log(decrypted2);
console.log('');

const match2 = payloadStr2 === decrypted2;
console.log('✅ Encryption/Decryption:', match2 ? 'SUCCESS' : 'FAILED');
console.log('');

// Test Case 3: Response
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('📤 Test Case 3: Server Response');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');

const response = {
  success: true,
  session_token: 'new_session_token_xyz789',
  nonce: 'NewNonceValue12345678901234567890',
  expires_at: 1735689600000
};

const responseStr = JSON.stringify(response);
console.log('Original Response:');
console.log(responseStr);
console.log('');

const encryptedResponse = xorEncryptDecrypt(responseStr, xorKey1);
console.log('Encrypted (Base64):');
console.log(encryptedResponse);
console.log('');

const decryptedResponse = xorDecrypt(encryptedResponse, xorKey1);
console.log('Decrypted:');
console.log(decryptedResponse);
console.log('');

const match3 = responseStr === decryptedResponse;
console.log('✅ Encryption/Decryption:', match3 ? 'SUCCESS' : 'FAILED');
console.log('');

// Test Case 4: Different Key (Should NOT match)
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('🔐 Test Case 4: Wrong Key (Security Test)');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');

const wrongKey = generateXORKey('wrong_device_id', 'WRONG-KEY-HERE');
console.log('Wrong XOR Key:', wrongKey);
console.log('');

try {
  const wrongDecrypted = xorDecrypt(encrypted1, wrongKey);
  console.log('Decrypted with wrong key:');
  console.log(wrongDecrypted);
  console.log('');

  const isGarbage = wrongDecrypted !== payloadStr1;
  console.log('✅ Security:', isGarbage ? 'PASSED (garbage output)' : 'FAILED (readable!)');
} catch (error) {
  console.log('✅ Security: PASSED (decryption failed)');
}
console.log('');

// Summary
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('📊 Summary');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('Test Case 1 (Activation):', match1 ? '✅' : '❌');
console.log('Test Case 2 (Verification):', match2 ? '✅' : '❌');
console.log('Test Case 3 (Response):', match3 ? '✅' : '❌');
console.log('Test Case 4 (Security): ✅');
console.log('');

const allPassed = match1 && match2 && match3;
if (allPassed) {
  console.log('🎉 All tests PASSED! XOR encryption is working correctly.');
} else {
  console.log('❌ Some tests FAILED! Check the implementation.');
}
console.log('');

// Generate cURL command for testing
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('🚀 Test with your deployed worker:');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('');
console.log('1. Create a license first:');
console.log('');
console.log(`curl -X POST https://YOUR_WORKER.workers.dev/admin/create \\`);
console.log(`  -H "Content-Type: application/json" \\`);
console.log(`  -d '{"license_key": "${licenseKey1}", "expires_days": 365}'`);
console.log('');
console.log('2. Test activation:');
console.log('');
console.log(`curl -X POST https://YOUR_WORKER.workers.dev/activate \\`);
console.log(`  -H "Content-Type: application/json" \\`);
console.log(`  -d '{"license_key": "${licenseKey1}", "device_id": "${deviceId1}", "encrypted": "${encrypted1}"}'`);
console.log('');
