"""Remaining Phase 8 checks on isolated phase8-test; OFF commands only.

Run with private IOT_API_KEY matching the running backend. Does not stop services.
Use --check outage only while Mosquitto is deliberately stopped and no equipment
relies on it. Use --check available before outage and after broker recovery to
check HTTP 202 without repeating completed telemetry/non-retention validation.
"""
import argparse
from datetime import datetime, timezone
import json
import os
import subprocess
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', choices=['non-retention', 'malformed', 'outage', 'available'], required=True)
    parser.add_argument('--backend-url', default='http://localhost:9090')
    args = parser.parse_args()
    key = os.environ.get('IOT_API_KEY')
    if not key:
        parser.error('Set IOT_API_KEY privately to match the backend')
    base = args.backend_url.rstrip('/') + '/api/iot/devices/phase8-test'
    topic = 'smartphset/devices/phase8-test/'

    def request(path, body=None):
        req = Request(base + path, data=None if body is None else json.dumps(body).encode(),
                      headers={'X-SmartPhset-IoT-Key': key, 'Content-Type': 'application/json'})
        try:
            with urlopen(req, timeout=5) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, None

    def publish(payload):
        subprocess.run(['mosquitto_pub', '-h', 'localhost', '-p', '1883', '-q', '0',
                        '-t', topic + 'telemetry', '-m', payload], check=True, timeout=10)

    def subscriber(seconds):
        return subprocess.Popen(['mosquitto_sub', '-h', 'localhost', '-p', '1883',
                                 '-t', topic + 'commands/fan', '-q', '0', '-C', '1',
                                 '-W', str(seconds)], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True)

    def finish(proc, timeout):
        try:
            return proc.communicate(timeout=timeout)
        finally:
            if proc.poll() is None:
                proc.kill()
                proc.wait()

    if args.check == 'non-retention':
        proc = subscriber(10)
        try:
            time.sleep(1)
            require(proc.poll() is None, 'Subscriber failed before publication')
            status, accepted = request('/actuators/fan', {'on': False})
            require(status == 202, 'OFF request was not accepted')
            output, _ = finish(proc, 12)
            require(proc.returncode == 0 and json.loads(output) == accepted,
                    'Live subscriber did not observe the exact new OFF command')
        finally:
            if proc.poll() is None:
                proc.kill()
                proc.wait()
        fresh = subscriber(3)
        output, error = finish(fresh, 5)
        require(fresh.returncode == 27 and not output.strip(),
                'Expected subscriber timeout with no retained command; ' + error.strip())
        print('PASS: live OFF command observed; fresh subscriber received no retained command')
    elif args.check == 'malformed':
        status, before = request('/state')
        require(status == 200, 'Existing synthetic state required')
        valid = {'device_id': 'phase8-test', 'temperature_c': 26.1,
                 'humidity_percent': 80.0, 'soil_moisture_percent': 60.0,
                 'captured_at': datetime.now(timezone.utc).isoformat()}
        invalid = ['{', '{}', 'null', 'x' * 2049,
                   json.dumps(dict(valid, device_id='wrong-device')),
                   json.dumps(dict(valid, humidity_percent=101)),
                   json.dumps(dict(valid, captured_at='invalid-time')),
                   json.dumps(dict(valid, captured_at='2099-01-01T00:00:00Z'))]
        for index, payload in enumerate(invalid, 1):
            publish(payload)
            time.sleep(0.5)
            status, state = request('/state')
            require(status == 200 and state == before,
                    f'Invalid telemetry case {index} changed state or broke REST')
        # A later valid reading proves the listener continues ingesting after rejection.
        valid['captured_at'] = datetime.now(timezone.utc).isoformat()
        publish(json.dumps(valid))
        deadline = time.monotonic() + 10
        while True:
            status, state = request('/state')
            if status == 200 and state['temperature_c'] == 26.1 and datetime.fromisoformat(
                    state['telemetry_captured_at']) == datetime.fromisoformat(valid['captured_at']):
                break
            require(time.monotonic() < deadline, 'Valid telemetry did not recover after invalid payloads')
            time.sleep(0.2)
        print('PASS: 8 invalid telemetry cases left state unchanged; subsequent valid telemetry persisted')
    elif args.check == 'available':
        status, _ = request('/actuators/fan', {'on': False})
        require(status == 202, 'Expected OFF command acceptance with HTTP 202 while online')
        print('PASS: backend accepts fan OFF with HTTP 202 while broker is available')
    else:
        time.sleep(6)  # Allow disconnect detection before checking the REST boundary.
        status, _ = request('/state')
        require(status == 200, 'Stored state unavailable during outage')
        status, _ = request('/actuators/fan', {'on': False})
        require(status == 503, 'Expected OFF command rejection with HTTP 503 during outage')
        print('PASS: backend reads stored state and rejects commands with 503 during outage')
        print('Restart Mosquitto, then run --check available to verify HTTP recovery without restarting Spring.')


if __name__ == '__main__':
    main()
