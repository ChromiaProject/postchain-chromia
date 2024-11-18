pipeline {
  agent any

  options {
    timeout(
      time: 1,
      unit: 'HOURS',
    )
  }

  environment {
    MAVEN_OPTS = "-Dhttps.protocols=TLSv1.2 -Dmaven.repo.local=/home/jenkins/.m2/repository -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=WARN -Dorg.slf4j.simpleLogger.showDateTime=true -Djava.awt.headless=true"
    MAVEN_CLI_OPTS = "--batch-mode -Dstyle.color=always --errors --fail-at-end --show-version -DinstallAtEnd=true -DdeployAtEnd=true -s .gitlab-settings.xml -U"

    POSTGRES_DB = "postchain"
    POSTGRES_USER = "postchain"
    POSTGRES_PASSWORD = "postchain"
    POSTGRES_INITDB_ARGS = "--lc-collate=C.UTF-8 --lc-ctype=C.UTF-8 --encoding=UTF-8"

    DOCKER_TLS_CERTDIR = ""
    DOCKER_DRIVER = "overlay2"
    DOCKER_CLI_EXPERIMENTAL = "enabled"

    TEST_MOUNT_DIRECTORY = "$WORKSPACE/mnt"
    TESTCONTAINERS_CHECKS_DISABLE = "true"

    TEST_BREAKDOWN_COMMAND = "/usr/bin/reset-mnt-permissions"
  }

  stages {
    stage('prepare environment') {
      steps {
        script {
          postgresContainerIpAddress = sh(
            script: """
              docker buildx rm postchain-builder || echo "Continuing anyway"
              docker buildx create --use --name postchain-builder --platform linux/amd64,linux/arm64,linux/arm/v8
              mkdir -p $TEST_MOUNT_DIRECTORY

              export POSTGRES_CONTAINER_ID=`docker run -d --name postgres -e POSTGRES_INITDB_ARGS="--lc-collate=C.UTF-8 --lc-ctype=C.UTF-8 --encoding=UTF-8" -e POSTGRES_PASSWORD=postchain -e POSTGRES_USER=postchain -p 5433:5432 postgres:14.14-alpine3.20@sha256:2bc30c8766a199d04c65abac9b4c08d0498cf96ebd256a02c31e8cc6ad95a4d6`

              docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' \$POSTGRES_CONTAINER_ID
            """,
            returnStdout: true,
          ).split('\n').last() // get only the last line from the output; the IP address to PostgreSQL

          env.CHR_DB_URL = "jdbc:postgresql://$postgresContainerIpAddress/postchain"
          env.POSTCHAIN_DB_URL = "jdbc:postgresql://$postgresContainerIpAddress/postchain"
        }
      }
    }

    stage('build') {
      when {
        not {
          expression {
            env.BRANCH_NAME in [
              'dev',
              'master',
            ]
          }
        }
      }

      steps {
        withCredentials([string(credentialsId: 'GITLAB_PAT_STRING', variable: 'GITLAB_PAT_STRING')]) {
          sh """
            env
            sed -i 's/<name>.*<\\/name>/<name>Private-Token<\\/name>/' .gitlab-settings.xml
            sed -i 's/<value>.*<\\/value>/<value>$GITLAB_PAT_STRING<\\/value>/' .gitlab-settings.xml

            mvn $MAVEN_CLI_OPTS --activate-profiles ci clean verify
          """
        }
      }
    }

    stage('deploy') {
      when {
        expression {
            env.BRANCH_NAME in [
              'dev',
//              'master',
            ]
        }
      }

      steps {
        withCredentials([
          string(credentialsId: 'GITLAB_PAT_STRING', variable: 'GITLAB_PAT_STRING'),
          string(credentialsId: 'DOCKER_AUTH_CONFIG', variable: 'DOCKER_AUTH_CONFIG'),
          string(credentialsId: 'CI_REGISTRY_USER', variable: 'CI_REGISTRY_USER'),
          string(credentialsId: 'CI_REGISTRY_PASSWORD', variable: 'CI_REGISTRY_PASSWORD'),
        ]) {
          sh """
            env

            sed -i 's/<name>.*<\\/name>/<name>Private-Token<\\/name>/' .gitlab-settings.xml
            sed -i 's/<value>.*<\\/value>/<value>$GITLAB_PAT_STRING<\\/value>/' .gitlab-settings.xml

            mvn $MAVEN_CLI_OPTS --activate-profiles ci,gitlab-registry,distro,slow-it -Dlatest.tag=latest-snapshot clean deploy
          """
        }
      }
    }
  }

  post {
    always {
      archiveArtifacts(
        artifacts: 'postchain-mc/pmc-directory/logs/*,postchain-mc/pmc-common/logs/*,chromia-infrastructure/logs/*,deployment-test/logs/*',
        fingerprint: true,
      )

      script {
        sh """
          docker stop postgres || true && docker rm postgres || true
        """
      }
    }
  }
}
