## CI (Continuous Integration, 지속적 통합)

- 개발자들이 작성한 코드 변경 사항을 공유 소스 코드 저장소(Git 등)에 **자주 자동으로 합치고(Merge), 빌드 및 테스트를 실행**하는 과정

## CD (Continuous Delivery / Continuous Deployment, 지속적 제공 및 배포)

### Continuous Delivery (지속적 제공)

- 코드가 테스트를 거쳐 언제든 배포될 수 있는 상태로 준비해 두는 것입니다.
- 실제 운영 환경(Production)으로의 최종 배포는 **사람이 수동으로 승인**한 뒤 진행합니다. 

### Continuous Deployment (지속적 배포)

- 모든 테스트와 검증 단계를 통과하면 **사람의 개입 없이 자동으로 운영 환경에까지 배포**되는 단계입니다.

  

  
# ci 실행

```
cd ~/IdeaProjects/ipo-calc
gh workflow run Quality --ref main
```

CI는 코드를 `main`에 반영하기 전에 자동으로 검사하는 절차

이 저장소의 `Quality` 워크플로는 GitHub에서 다음을 실행
- **UI**: 의존성 설치 → 테스트 → lint → 타입 검사 → 포맷 검사 → 빌드
- **백엔드**: PostgreSQL을 띄운 뒤 테스트 → Checkstyle → 포맷 검사 → 빌드

&nbsp;

ci 실행 결과를 확인

```
gh run watch
```


&nbsp;

# 배포

```
cd /Users/joy/IdeaProjects/ipo-calc/backend
./gradlew clean bootJar

cd /Users/joy/IdeaProjects/ipo-calc/ui
pnpm install --frozen-lockfile
pnpm build

cd /Users/joy/IdeaProjects/ipo-calc/infra/ansible
ansible-playbook deploy-backend.yml --ask-pass --ask-become-pass
ansible-playbook deploy-ui.yml --ask-pass --ask-become-pass

```