# 추가 수집 및 복원 시 필요한 자료

| 항목 | 확인 근거 / 현재 상태 |
|---|---|
| `/usr/local/sbin/redweb-xdr-firewall-base` | `systemd/redweb-xdr-firewall-base.service`의 ExecStart에서 호출하지만 최초 ZIP에는 실행 파일이 없음 |
| `/var/ossec/active-response/bin/redweb-web-guard.py` | `wazuh/ossec.conf.example`에서 사용자 정의 `redweb-web-guard` 명령의 실행 파일로 지정되지만 최초 ZIP에 없음 |
| Grafana 대시보드·경보·알림 정책 | provisioning 파일은 Prometheus 데이터 소스만 수집됨. DB에 저장된 사용자 설정은 별도 내보내기 필요 |
| Grafana 연락처 | Discord Webhook 값은 저장소에 포함하지 않음. 복원 시 별도 비밀 값으로 설정 |
| `.env` 및 보호 목록 실제 값 | 의도적으로 제외. 관리자가 별도 비밀 저장소에서 관리해야 함 |
| Oracle 컨테이너·데이터 | 이 모니터링 Compose는 외부 Oracle 인스턴스에 연결하는 Exporter만 정의. DB 자체 배포 정보·데이터 볼륨은 별도 보관 대상 |
| Tomcat 기동·JMX Agent | JMX 수집 규칙은 있지만 Tomcat 서비스의 Java Agent 기동 설정·JAR은 이번 ZIP의 수집 범위 밖 |
| 운영 상태 | SQLite Outbox·체크포인트, Grafana DB, Prometheus·Loki 저장 데이터는 포함하지 않음 |

최초 수집 도구가 두 실행 파일의 경로를 포함하지 못한 점을 확인하여 `tools/export_redweb_infra.py`의 수집 범위를 보완했습니다. 이후 ZIP을 생성하면 해당 파일이 존재할 때 함께 수집합니다. 실행 파일이 없는 상태에서 현재 서비스 파일만 설치하거나 방어 모드를 켜지 않습니다.
