## BOSH release for Redis

Infrastructure 에 redis 를 빠르게 제공하기 위한 방법으로 redis bosh-release를 사용할 수 있습니다. 이는 k8s 환경 또는 Quarks/cf-operator 및 Helm chart를 통해서도 배포될 수 있습니다.

#### 전제 조건

* BOSH Director 가 설치되어 있어야 합니다.
* git cli 가 설치 되어 있어야 합니다.
* bosh cli 설치 및 BOSH Director 에 대한 target 이 설정되어 있어야 합니다.
* Public/Private IaaS 가 준비되어 있어야 합니다.

#### 설치

전제 조건에서 언급한 redis-boshrelease 의 git repository는 기본 manifest 파일 및 operator 파일을 포함하고 있습니다. 이들은 사용하고자 하는 배포 환경에 초기 배포나 그 이후 업그레이드를 위해 사용될 수 있습니다.



##### 1. 2-node cluster 설치

```
$ export BOSH_ENVIRONMENT=<alias>
$ export BOSH_DEPLOYMENT=redis

$ git clone https://github.com/cloudfoundry-community/redis-boshrelease.git
$ bosh deploy redis-boshrelease/manifests/redis.yml
```

BOSH 에서 Credhub/Config server 가 존재하지 않는다면, --vars-store flag 를 통해 password 및 certificate 생성을 허용합니다.
```
bosh deploy redis-boshrelease/manifests/redis.yml --vars-store creds.yml
```

설치 도중 ```Instance group 'redis' references an unknown vm type 'default'``` 또는 유사한 에러가 발생한 경우, BOSH Cloud Config 에 다음 스크립트를 사용하여 ```vm_type``` 과 ```network``` 를 인프라 환경에 맞게 지정할 수 있습니다. 
```
bosh -d redis deploy redis-boshrelease/manifests/redis.yml -o <(./manifests/operators/pick-from-cloud-config.sh)
```



##### 2. Sentinel 설치

운영 환경에 있어서 Redis 는 일반적으로 master 와 replica로 구성됩니다. 이는 운영중 예기치 않은 이유로 인해 master 가 다운되는 경우 관리자가 이를 감지하여 replica 를 master 로 올리고 client 들이 새로운 master 에 접근할 수 있도록 해야되기 때문입니다.

Sentinel 은 상기 구성에서 master 와 replica 를 모니터링하고 있다가 master가 다운될 경우 이를 감지하여 관리자의 직접 개입 없이 자동으로 replica를 마스터로 올려주는 기능을 수행합니다.

Sentinel 은 Redis 서버에서 Redis process 와 동일하게 동작하고 다음과 같은 기능을 수행합니다.

* 모니터링 Monitoring: Sentinel 은 master, replica 가 제대로 동작하는지 지속적으로 감시합니다.
* 자동 장애조치 Automatic Failover: Sentinel 은 master 가 예기치 않은 이유로 다운되었을 경우, 이를 감지하여 replica 를 새로운 master로 승격시켜 줍니다. 또한 replica 가 여러대 있을 경우, 이러한 replica 들이 새로운 master 로부터 데이터를 받을 수 있도록 재 구성할 수 있습니다.

설치과정

내려 받은 패키지 경로에서 ```redis.yml``` 을 다음과 같이 수정하여 상기 1. 항목의 2-node cluster 와 동일한 방법으로 설치합니다.
```
[...]
  instances: 3
  jobs:
[...]
  - name: redis
    release: redis
  - name: redis-sentinel
    release: redis
  properties:
    bind_static_ip: true
    password: ((redis_password)
```
또는 다음 ```use-sentinel-addresses.yml``` 을 옵션 flag 를 통해 지정하여 배포할 수도 있습니다.
```
bosh -d redis deploy manifests/redis.yml -o <(./manifests/operators/pick-from-cloud-config.sh) -o ./manifests/operators/use-sentinel-addresses.yml
```

배포가 성공적으로 수행되었을 경우 2 node 또는 3 node의 Redis 인스턴스가 생성된 것을 확인할 수 있으며,
bosh ssh 를 통해 해당 인스턴스에 떠있는 프로세스를 확인할 수 있습니다. 

#### Failover 테스트

Redis 마스터를 중지시켜 Sentinel 이 replica 를 master로 승격 유무를 확인하는 테스트

```
Deployment 'redis'

Instance                                    Process State  AZ               IPs        VM CID               VM Type   Active  Stemcell
redis/UUID1                                 running        ap-northeast-2a  IP_HOST1  INSTANCE_ID1  t3.micro  true    bosh-aws-xen-hvm-ubuntu-xenial-go_agent/621.64
redis/UUID2                                 running        ap-northeast-2a  IP_HOST2  INSTANCE_ID2  t3.micro  true    bosh-aws-xen-hvm-ubuntu-xenial-go_agent/621.64
redis/UUID3                                 running        ap-northeast-2a  IP_HOST3  INSTANCE_ID3  t3.micro  true    bosh-aws-xen-hvm-ubuntu-xenial-go_agent/621.64

3 vms
```
상기 리스트와 같이 Redis Sentinel 이 구성되어 있습니다.

현재 master는 redis/UUID1(IP_HOST1) 로 지정되어 있습니다. 이를 shutdown 시킨 후 sentinel 프로세스의 로그를  확인합니다

```
7:X 15 Jun 05:33:23.185 # oO0OoO0OoO0Oo Redis is starting oO0OoO0OoO0Oo
7:X 15 Jun 05:33:23.185 # Redis version=4.0.14, bits=64, commit=00000000, modified=0, pid=7, just started
7:X 15 Jun 05:33:23.185 # Configuration loaded
7:X 15 Jun 05:33:23.187 # Not listening to IPv6: unsupproted
7:X 15 Jun 05:33:23.187 * Running mode=sentinel, port=26379.
7:X 15 Jun 05:33:23.192 # Sentinel ID is 9e9720548f4e9e6d9561ef519ec6c7a8c6d994d7
7:X 15 Jun 05:33:23.192 # +monitor master mymaster IP_HOST1 6379 quorum 2
7:X 15 Jun 05:33:23.193 * +slave slave IP_HOST2:6379 IP_HOST2 6379 @ mymaster IP_HOST1 6379
7:X 15 Jun 05:33:23.195 * +slave slave IP_HOST3:6379 IP_HOST3 6379 @ mymaster IP_HOST1 6379
7:X 15 Jun 05:33:23.824 * +sentinel sentinel 30b2b318caa344108af02cf3d99e537e00d06176 IP_HOST1 26379 @ mymaster IP_HOST1 6379
7:X 15 Jun 05:33:24.758 * +sentinel sentinel f5f51fc7d8ead57e7deccbac1a6a2b161a6ff49b IP_HOST2 26379 @ mymaster IP_HOST1 6379
7:X 15 Jun 06:27:36.817 # +sdown sentinel 30b2b318caa344108af02cf3d99e537e00d06176 IP_HOST1 26379 @ mymaster IP_HOST1 6379
7:X 15 Jun 06:27:36.879 # +sdown master mymaster IP_HOST1 6379
7:X 15 Jun 06:27:36.977 # +new-epoch 1
7:X 15 Jun 06:27:36.978 # +vote-for-leader f5f51fc7d8ead57e7deccbac1a6a2b161a6ff49b 1
7:X 15 Jun 06:27:37.470 # +config-update-from sentinel f5f51fc7d8ead57e7deccbac1a6a2b161a6ff49b IP_HOST2 26379 @ mymaster IP_HOST1 6379
7:X 15 Jun 06:27:37.470 # +switch-master mymaster IP_HOST1 6379 IP_HOST3 6379
7:X 15 Jun 06:27:37.470 * +slave slave IP_HOST2:6379 IP_HOST2 6379 @ mymaster IP_HOST3 6379
7:X 15 Jun 06:27:37.470 * +slave slave IP_HOST1:6379 IP_HOST1 6379 @ mymaster IP_HOST3 6379
7:X 15 Jun 06:27:42.559 # +sdown slave IP_HOST1:6379 IP_HOST1 6379 @ mymaster IP_HOST3 6379
```

sentinel 이 자동으로 IP_HOST3 인스턴스를 master로 전환하는 것을 보여주고 이를 업데이트 함을 확인할 수 있습니다.
