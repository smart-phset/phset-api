"""Opt-in real Mosquitto -> Spring -> PostgreSQL and OFF-command acceptance.

Requires a developer/test backend, mosquitto_pub/sub and private IOT_API_KEY.
Publishes synthetic readings for --device (default phase8-test), which
must be in MQTT_DEVICES. Does not energize a real actuator or claim board testing.
"""
import argparse
from datetime import datetime, timezone
import json
import os
import subprocess
import time
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend-url', default='http://localhost:9090')
    parser.add_argument('--broker-host', default='localhost')
    parser.add_argument('--broker-port', type=int, default=1883)
    parser.add_argument('--device', default='phase8-test')
    args = parser.parse_args()
    key = os.environ.get('IOT_API_KEY')
    if not key:
        parser.error('Set IOT_API_KEY privately to match the backend')
    if not args.device.replace('-', '').replace('_', '').isalnum() or len(args.device) > 64:
        parser.error('Invalid device ID')
    common = ['-h', args.broker_host, '-p', str(args.broker_port)]
    prefix = 'smartphset/devices/' + args.device
    def publish(suffix, payload):
        subprocess.run(['mosquitto_pub', *common, '-q', '0', '-t', prefix+'/'+suffix,
                        '-m', json.dumps(payload)], check=True, timeout=10)
    def request(path, payload=None):
        req = Request(args.backend_url.rstrip('/')+'/api/iot/devices/'+args.device+path,
                      data=json.dumps(payload).encode() if payload is not None else None,
                      headers={'X-SmartPhset-IoT-Key': key, 'Content-Type': 'application/json'})
        with urlopen(req, timeout=5) as response:
            return response.status, json.load(response)
    timestamp = datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')
    publish('telemetry', {'device_id': args.device, 'captured_at': timestamp,
                         'temperature_c': 25.8, 'humidity_percent': 87.0,
                         'soil_moisture_percent': 68.0})
    publish('state', {'device_id': args.device, 'captured_at': timestamp,
                      'fan': False, 'light': False, 'pump': False})
    deadline = time.monotonic()+10
    while True:
        try:
            status, state = request('/state')
            assert status == 200 and state['temperature_c'] == 25.8
            assert state['soil_moisture_percent'] == 68.0 and state['pump'] is False
            assert datetime.fromisoformat(state['telemetry_captured_at']) == datetime.fromisoformat(timestamp)
            break
        except Exception:
            if time.monotonic() >= deadline:
                raise
            time.sleep(0.2)
    with subprocess.Popen(['mosquitto_sub', *common, '-t', prefix+'/commands/fan',
                           '-q', '0', '-C', '1', '-W', '10'], stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL, text=True) as subscriber:
        try:
            time.sleep(0.5)  # Let the external CLI complete its subscription.
            status, accepted = request('/actuators/fan', {'on': False})
            assert status == 202 and accepted['on'] is False
            output, _ = subscriber.communicate(timeout=12)
            assert subscriber.returncode == 0
            observed = json.loads(output)
            assert observed == accepted and observed['expires_at'] >= time.time()-1
        finally:
            if subscriber.poll() is None:
                subscriber.kill()
                subscriber.wait()
    print('PASS: real MQTT telemetry/state persistence and backend OFF command observed')
    print('Hardware acceptance remains pending; no physical device response was asserted.')


if __name__ == '__main__':
    main()
