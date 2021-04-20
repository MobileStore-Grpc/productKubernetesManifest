pipeline {
  agent any

  stages {  
    stage("Execute DA_SearchDelegator_Staging_K8S") {
      steps {
          build 'DA_SearchDelegator_Staging_K8S'
      }
    }  
    
    stage("Application Code Checkout") {
      steps {
        git branch: 'release/release-v7.3.2', credentialsId: 'Anjan-Gitlab', url: 'https://gitlab.com/providersearch/search.git'
      }
    }
    
    stage('Build Jar with Maven') {
      steps {
        withMaven(maven: 'maven3.6') {
          sh label: 'Creating Artifact', script: "mvn clean package -DskipTests -Dspring.profiles.active=staging"
        }
      }
    }

    stage('Sonarqube Analysis') {
       steps {
         withMaven(maven: 'maven3.6') {
           withSonarQubeEnv('SonarQubeJava') {
             sh label: 'SonarQube Scan', script: "mvn sonar:sonar -Dsonar.projectKey=da-staging-search -Dsonar.projectVersion=1.0 -Dsonar.projectName=da-staging-search -Dsonar.login=06ca9862c9a2ece34196a8c0304d89677071ee70 -Dsonar.sources=src/main/java/ -Dsonar.java.binaries=target/classes -Dsonar.language=java -Dsonar.sourceEncoding=UTF-8"
          }
        }
      }
    }

    stage('Managing Jar Artifact') {
      steps {
        sh label: 'Placing Artifact in Build Context', script: 'mv target/*.jar /tmp/search/search.jar'
      }
    }
    
    stage("Kubernetes Code Checkout") {
      steps {
        git branch: 'stage', credentialsId: '7ad56188-0f59-400f-9d8f-8a9c5303ac70', url: 'https://gitlab.com/DA-DevOps/application/search.git'
      }
    }
    
    stage('Creating Build Context') {
      steps {
        sh label: 'Placing Artifact in Build Context', script: 'mkdir build-context && mv /tmp/search/search.jar build-context/search.jar'
        sh label: 'Placing Dockerfile in Build Context', script: 'cp Dockerfile build-context/'
      }
    }
    
    stage('Creating Docker Image') {
      steps {
        sh label: 'Docker Image Creation', script: 'cd build-context && docker build -t 614680000114.dkr.ecr.us-east-1.amazonaws.com/search:${BUILD_NUMBER} .'
      }
    }
    
    
    stage('Pushing Docker Image to Private Repository') {
      steps {
        sh label: 'Docker Registry Login', script: 'eval $(aws ecr get-login --no-include-email --region us-east-1)'
        sh label: 'Docker Image Push', script: 'docker push 614680000114.dkr.ecr.us-east-1.amazonaws.com/search:${BUILD_NUMBER}'
      }
    }
    
    stage('Creating Deploy Context') {
      steps {
        sh label: 'Docker Registry Login', script: 'eval $(aws ecr get-login --no-include-email --region us-east-1)'
        sh label: 'Creating Deployment Manifest', script: 'cp *.yaml build-context/' 
        sh label: 'Replace Image Tag', script: 'sed -i "s/TAG/${BUILD_NUMBER}/g" build-context/search-app.yaml'
        sh label: 'Updating Registry Secret', script: 'kubectl create secret generic regcred --from-file=.dockerconfigjson=/var/jenkins_home/.docker/config.json  --type=kubernetes.io/dockerconfigjson --namespace=appstaging --dry-run=true -o yaml | kubectl apply -f -' 
      }
    }
    
    stage('Deploy Latest Version') {
      steps {
        sh label: 'Applying Kubectl Updates', script: 'kubectl apply -f build-context/'
      }
    }
    
  }
  
  post { 
    always { 
    emailext (
            body: '${FILE,path="/var/jenkins_home/jenkins-email-html/email-report.html"}',
            mimeType: 'text/HTML', 
            replyTo: 'no-reply@docasap.com', 
            subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!', 
            to: 'sajal.jain@docasap.com',
            recipientProviders: [developers(), requestor(), culprits()]
            )
    cleanWs()
    }
  }
  
}

