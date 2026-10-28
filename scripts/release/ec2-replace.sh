#!/usr/bin/env bash
# Prepared for the inspected EC2 docker-run deployment. Do not execute without approval.
# Usage: sudo APPROVE_PRODUCTION_DEPLOY=yes bash ec2-replace.sh repository@sha256:digest /absolute/camera.env
set -euo pipefail
[[ ${APPROVE_PRODUCTION_DEPLOY:-} == yes ]] || { echo 'Requires explicit production approval and APPROVE_PRODUCTION_DEPLOY=yes.' >&2; exit 1; }
[[ $EUID == 0 ]] || { echo 'Run through sudo to protect private snapshots.' >&2; exit 1; }
image=${1:?Supply the pushed, tested image digest}
camera_env=${2:?Supply a private file containing only the two new camera variables}
[[ "$image" =~ ^ratanaksaren/smartphset-api@sha256:[a-f0-9]{64}$ ]] || exit 1
[[ "$camera_env" == /* && -f "$camera_env" ]] || exit 1
umask 077
release="/opt/smartphset/releases/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$release"
docker inspect smartphset-api > "$release/container.json"
# Sensitive snapshot: never print, copy to Git or upload it.
python3 - "$release" "$camera_env" "$(dirname "$0")/expected-hostconfig.json" <<'PY'
import json,sys,pathlib,os
p=pathlib.Path(sys.argv[1]); d=json.loads((p/'container.json').read_text())[0]; h=d['HostConfig']
assert d['State']['Running'], 'Existing API must be running'
actual=dict(h); actual.pop('ConsoleSize',None)
assert actual==json.loads(pathlib.Path(sys.argv[3]).read_text()), 'HostConfig drift: stop and review the deployment command'
assert h['NetworkMode']=='smartphset-net' and len(d['NetworkSettings']['Networks'])==1
assert h['PortBindings']=={'9090/tcp':[{'HostIp':'127.0.0.1','HostPort':'9090'}]}
assert not d['Mounts'] and not h.get('Binds')
assert h['RestartPolicy']=={'Name':'unless-stopped','MaximumRetryCount':0}
for k in ['Memory','NanoCpus','CpuShares','CpuPeriod','CpuQuota','MemoryReservation','MemorySwap','OomScoreAdj']:
 assert not h.get(k), 'Runtime drift: '+k
for k in ['CapAdd','CapDrop','SecurityOpt','Ulimits','PidsLimit','ExtraHosts','Dns','DnsSearch','Devices','DeviceRequests','Privileged','ReadonlyRootfs','AutoRemove','Init']:
 assert not h.get(k), 'Runtime drift: '+k
assert h['LogConfig']=={'Type':'json-file','Config':{}}
assert h.get('ShmSize')==67108864 and h.get('Runtime')=='runc'
assert d['Config']['Entrypoint']==['java','-jar','/app/app.jar'] and not d['Config'].get('Cmd')
assert not d['Config'].get('Healthcheck'), 'Healthcheck drift; review before replacement'
assert d['Config'].get('WorkingDir')=='/app'
assert not d['Config'].get('Tty') and not d['Config'].get('OpenStdin')
camera=pathlib.Path(sys.argv[2]); assert camera.stat().st_mode & 0o077 == 0, 'Camera file must be mode 600'
values={}
for line in camera.read_text().splitlines():
 if not line or line.startswith('#'): continue
 k,v=line.split('=',1); assert k in ['SMARTPHSET_CAMERA_API_KEY','SMARTPHSET_CAMERA_REGISTRY'] and k not in values
 assert v and not any(t in v.upper() for t in ['REPLACE','YOUR_','CHANGE_ME']); values[k]=v
assert len(values)==2 and len(values['SMARTPHSET_CAMERA_API_KEY'])>=32
# Preserve running application/JVM environment, including settings absent from api.env.
# Base-image platform variables must come from the NEW image.
platform={'PATH','JAVA_HOME','LANG','LANGUAGE','LC_ALL','JAVA_VERSION'}
env=[s for s in d['Config']['Env'] if s.split('=',1)[0] not in platform|values.keys()]
assert all('\n' not in s and '\r' not in s for s in env)
env += [k+'='+v for k,v in values.items()]
(p/'runtime.env').write_text('\n'.join(env)+'\n'); os.chmod(p/'runtime.env',0o600)
(p/'previous-image-id').write_text(d['Image']+'\n')
PY
# Preserve private env and Nginx config independently for rollback.
cp -p /opt/smartphset/api.env "$release/api.env.before"
cp -p /etc/nginx/conf.d/phset.api.ratanak.me.conf "$release/nginx.before.conf"
docker pull "$image"
old="smartphset-api-rollback-$(basename "$release")"
printf '%s\n' "$old" > "$release/rollback-container"
# Never run old and new backends concurrently against production MQTT.
docker stop smartphset-api >/dev/null
if ! docker rename smartphset-api "$old"; then
  docker start smartphset-api >/dev/null
  exit 1
fi
restore() {
  echo "Replacement failed. Restoring retained container $old" >&2
  if docker container inspect smartphset-api >/dev/null 2>&1; then
    docker stop smartphset-api >/dev/null || true
    docker rename smartphset-api "smartphset-api-failed-$(basename "$release")"
  fi
  docker rename "$old" smartphset-api
  docker start smartphset-api >/dev/null
}
if ! docker run -d --name smartphset-api --restart unless-stopped \
  --network smartphset-net --env-file "$release/runtime.env" \
  -p 127.0.0.1:9090:9090 "$image" >/dev/null; then restore; exit 1; fi
if ! curl --fail --silent --show-error --retry 40 --retry-delay 3 --retry-all-errors \
  http://127.0.0.1:9090/actuator/health; then restore; exit 1; fi
printf '\nAPI replaced; retained rollback container: %s\nPrivate release snapshot: %s\n' "$old" "$release"
echo 'Finish keyed IoT/camera checks and the separately approved Nginx update. Do not delete rollback evidence.'
