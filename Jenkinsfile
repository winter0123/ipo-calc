pipeline {
  agent any
   environment {
     COMPOSE = 'docker compose -p ipo-calc -f deploy/docker-compose.prod.yml'
     CI_DB = 'jdbc:postgresql://ipo-calc-postgres-ci-dev:5432/vibe_ai_team'
   }
  stages {
    stage('Test backend') {
      steps {
         sh '''
           docker rm -f -v ipo-calc-postgres-ci-dev || true
           docker run -d --name ipo-calc-postgres-ci-dev --network ci-net --label com.docker.compose.project=ipo-calc \
             -e POSTGRES_DB=vibe_ai_team -e POSTGRES_USER=vibe -e POSTGRES_PASSWORD=vibe_local_password postgres:17
           until docker exec ipo-calc-postgres-ci-dev pg_isready -U vibe -d vibe_ai_team; do sleep 1; done
          cd backend
          SPRING_DATASOURCE_URL=$CI_DB SPRING_DATASOURCE_PASSWORD=vibe_local_password DB_PASSWORD=vibe_local_password \
            ./gradlew test checkstyleMain checkstyleTest --no-daemon
        '''
      }
       post { always { sh 'docker rm -f -v ipo-calc-postgres-ci-dev || true' } }
    }
    stage('Test ui') {
      steps {
        dir('ui') {
          sh 'pnpm install --frozen-lockfile && pnpm run lint && pnpm run typecheck && pnpm run format:check && pnpm run test && pnpm run build'
        }
      }
    }
    stage('E2E + A11y') {
      steps {
        // 일회용 DB(ipo-calc-postgres-e2e-dev) + 빈 포트 백엔드/vite 로 Playwright 시나리오 전체 실행 후 접근성 스캔. 하나라도 FAIL이면 run.sh가 1로 종료.
         sh '''
           docker rm -f -v ipo-calc-postgres-e2e-dev || true
           docker run -d --name ipo-calc-postgres-e2e-dev --network ci-net --label com.docker.compose.project=ipo-calc \
             -e POSTGRES_DB=vibe_e2e -e POSTGRES_USER=vibe -e POSTGRES_PASSWORD=vibe_local_password postgres:17
           until docker exec ipo-calc-postgres-e2e-dev pg_isready -U vibe -d vibe_e2e; do sleep 1; done
           E2E_DB_CONTAINER=ipo-calc-postgres-e2e-dev E2E_DB_NAME=vibe_e2e E2E_JDBC_URL=jdbc:postgresql://ipo-calc-postgres-e2e-dev:5432/vibe_e2e \
             DB_PASSWORD=vibe_local_password tools/e2e/run.sh
        '''
        // 같은 컨테이너의 DB를 비우고 접근성 스캔(63화면 미달·위반이면 1로 종료 → 배포 중단).
         sh '''
           docker exec ipo-calc-postgres-e2e-dev psql -U vibe -d postgres -c "DROP DATABASE vibe_e2e WITH (FORCE)" -c "CREATE DATABASE vibe_e2e"
           E2E_DB_CONTAINER=ipo-calc-postgres-e2e-dev E2E_DB_NAME=vibe_e2e E2E_JDBC_URL=jdbc:postgresql://ipo-calc-postgres-e2e-dev:5432/vibe_e2e \
             DB_PASSWORD=vibe_local_password tools/accessibility-scan/run-scan.sh
        '''
      }
       post { always { sh 'docker rm -f -v ipo-calc-postgres-e2e-dev || true' } }
    }
    stage('Deploy') {
      steps { sh '$COMPOSE up -d --build' }
    }
    stage('Smoke') {
      steps {
        // 비로그인 /auth/me 가 401 이면 backend가 떠서 DB·마이그레이션까지 통과한 것
        sh '''
          for i in $(seq 1 30); do
            if $COMPOSE exec -T prod-backend wget -S -O /dev/null http://localhost:8080/auth/me 2>&1 | grep -q "HTTP/1.1 401"; then
              echo "smoke OK"; exit 0
            fi
            sleep 2
          done
          echo "smoke FAILED"; exit 1
        '''
      }
    }
  }
}
