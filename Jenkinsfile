// This pipeline requires the docker pipeline plugin and the use of
// a jenkins/jnlp-agent-docker agent. This agent must be run as user/group "jenkins:docker" (may need to use group ID instead of group name)
pipeline {
    agent { label 'docker' }

    stages {
        stage('Build'){
            steps {
                checkout scm
                script {
                    def props = readProperties file: '.properties'  // readProperties requires pipeline utility steps plugin
                    version = props.version
                }
                sh "docker build -t ${LOCAL_REG_URL}/bmedia_api:${version}_SNAPSHOT -f ./docker/api/ApiDockerfile ."
                sh "docker push ${LOCAL_REG_URL}/bmedia_api:${version}_SNAPSHOT"
            }            
        }
        stage('Test'){
            steps {
                echo "TODO..."
            }
        }
        stage('Deploy'){
            steps{
                echo "TODO"
            }            
        }
    }
}