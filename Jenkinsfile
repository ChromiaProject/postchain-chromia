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
    CHR_DB_URL = "jdbc:postgresql://postgres/postchain"
    POSTCHAIN_DB_URL = "jdbc:postgresql://postgres/postchain"
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
        sh """
          docker buildx rm postchain-builder || echo "Continuing anyway"
          docker buildx create --use --name postchain-builder --platform linux/amd64,linux/arm64,linux/arm/v8
          mkdir -p $TEST_MOUNT_DIRECTORY
        """
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
            sed -i 's/<name>.*<\\/name>/<name>Private-Token<\\/name>/' .gitlab-settings.xml
            sed -i 's/<value>.*<\\/value>/<value>$GITLAB_PAT_STRING<\\/value>/' .gitlab-settings.xml

            mvn $MAVEN_CLI_OPTS --activate-profiles ci verify
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

            mvn $MAVEN_CLI_OPTS --activate-profiles ci,gitlab-registry,distro,nightly deploy
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
    }
  }
}
