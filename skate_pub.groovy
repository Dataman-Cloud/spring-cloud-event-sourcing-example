publicRegistryUrl = "demoregistry.dataman-inc.com"
publicImagePrefix = "${publicRegistryUrl}/skate"
publicRegistryUsername = "guangzhou"
publicRegistryPassword = "Gz-regsistry-2017@"
workRootDir = "/home/apps/jenkins-home/workspace/skate"
publicRepositoryId = "releases-public"
publicRepositoryUrl = "http://106.75.3.66:8081/nexus/content/repositories/releases"
workRootDir = "/home/apps/jenkins-home/workspace/skate"

node("master") {
    stage("Common") {
        echo "Environment: Version: ${VERSION}"
        //前置检查
        //1. develop分支不需带上版本号, master分支需要带上版本号
          if (params.VERSION != "") {
              error("Only [master] branch can have version. Please check your input!")
          }

        //2. 环境检查
        if (params.ENV != "test" && params.ENV != "release") {
            error("[Environment] should be test, release")
        }

        //3. 发布master到生产之前进行二次确认
        if (params.ENV == "release") {
            try {
                timeout(time: 15, unit: 'SECONDS') {
                    input message: '将会直接直接发布Release, 确定要发布吗',
                            parameters: [[$class      : 'BooleanParameterDefinition',
                                          defaultValue: false,
                                          description : '点击将会发布Release',
                                          name        : '发布Release']]
                }
            } catch (err) {
                def user = err.getCauses()[0].getUser()
                error "Aborted by:\n ${user}"
            }
        }
    }
}
/** 推3rd依赖包到公网的Maven仓库 **/
def push3rdJarToPublicMaven() {

    if (params.IS_PUSH == "Yes") {
        // 推送依赖包到公网仓库，不能包含有源码
        stage("Push-public-parent") {
            //推送skate-parent
            //由于parent没有jar包，而且mvn命令一定要写-Dfile参数，所以-Dfile这里随便写一个，实际上不会推此包
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=pom.xml -Dfile=octopus-api/target/octopus-api-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "echo 'push octopus-parent pom to public maven registry finish.'"
        }

        stage("Push-public-base") {
            //推送 基础配置，服务发现
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=config-service/pom.xml -Dfile=config-service/target/config-service-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=discovery-service/pom.xml -Dfile=discovery-service/target/discovery-service-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=edge-service/pom.xml -Dfile=edge-service/target/edge-service-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
//            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=hystrix-dashboard/pom.xml -Dfile=hystrix-dashboard/target/hystrix-dashboard-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "echo 'push base service jar to public maven registry finish.'"
        }

        stage("Push-public-biz") {
            //推送 业务
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=user-service/pom.xml -Dfile=user-service/target/octopus-api-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=account-service/pom.xml -Dfile=account-service/target/account-service-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=shopping-cart-service/pom.xml -Dfile=shopping-cart-service/target/shopping-cart-service-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=catalog-service/pom.xml -Dfile=catalog-service/target/catalog-service-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=inventory-service/pom.xml -Dfile=inventory-service/target/inventory-service-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=order-service/pom.xml -Dfile=order-service/target/order-service-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"
            sh "mvn -s ${workRootDir}/settings.xml deploy:deploy-file -DpomFile=online-store-web/pom.xml -Dfile=online-store-web/target/online-store-web-${VERSION}.jar  -DrepositoryId=${publicRepositoryId} -Durl=${publicRepositoryUrl}"

            sh "echo 'push biz jar to public maven registry finish.'"
        }
    }
}

/** 推images到公网的仓库**/
def pushImageToPublicRegistry() {

    if (params.IS_PUSH == "Yes") {

        stage("Push-image-base") {
            sh "docker build -t ${publicImagePrefix}/config-service:${VERSION} config-service"
            sh "docker build -t ${publicImagePrefix}/discovery-service:${VERSION} discovery-service"
            sh "docker build -t ${publicImagePrefix}/edge-service:${VERSION} edge-service"
            sh "docker login -u ${publicRegistryUsername} -p ${publicRegistryPassword} ${publicRegistryUrl}"
            sh "docker push ${publicImagePrefix}/config-service:${VERSION}"
            sh "docker push ${publicImagePrefix}/discovery-service:${VERSION}"
            sh "docker push ${publicImagePrefix}/edge-service:${VERSION}"
        }

        stage("Push-image-biz") {
            sh "docker build -f user-service/${targetdockerfile}/Dockerfile -t ${publicImagePrefix}/user-service:${VERSION} user-service"
            sh "docker build -f account-service/${targetdockerfile}/Dockerfile -t ${publicImagePrefix}/account-service:${VERSION} account-service"
            sh "docker build -f shopping-cart-service/${targetdockerfile}/Dockerfile -t ${publicImagePrefix}/shopping-cart-service:${VERSION} shopping-cart-service"
            sh "docker build -f catalog-service/${targetdockerfile}/Dockerfile -t ${publicImagePrefix}/catalog-service:${VERSION} catalog-service"
            sh "docker build -f inventory-service/${targetdockerfile}/Dockerfile -t ${publicImagePrefix}/inventory-service:${VERSION} inventory-service"
            sh "docker build -f order-service/${targetdockerfile}/Dockerfile -t ${publicImagePrefix}/order-service:${VERSION} order-service"
            sh "docker build -f online-store-web/${targetdockerfile}/Dockerfile -t ${publicImagePrefix}/online-store-web:${VERSION} online-store-web"

            sh "docker login -u ${publicRegistryUsername} -p ${publicRegistryPassword} ${publicRegistryUrl}"

            sh "docker push ${publicImagePrefix}/user-service:${VERSION}"
            sh "docker push ${publicImagePrefix}/account-service:${VERSION}"
            sh "docker push ${publicImagePrefix}/shopping-cart-service:${VERSION}"
            sh "docker push ${publicImagePrefix}/catalog-service:${VERSION}"
            sh "docker push ${publicImagePrefix}/inventory-service:${VERSION}"
            sh "docker push ${publicImagePrefix}/order-service:${VERSION}"
            sh "docker push ${publicImagePrefix}/online-store-web:${VERSION}"
        }
    }
}
