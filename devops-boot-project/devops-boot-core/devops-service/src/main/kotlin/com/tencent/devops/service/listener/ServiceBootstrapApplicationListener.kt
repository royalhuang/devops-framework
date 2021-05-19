package com.tencent.devops.service.listener

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.Ordered
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.util.ClassUtils
import java.util.Properties

/**
 * 用于向ConfigDataEnvironment注入公共配置，用于引导springboot统一加载外部配置
 *
 * TODO: SpringCloud 2020.0+ 默认关闭了bootstrap context, 但SpringCloud Kubernetes还是依赖了bootstrap.yaml,
 * 等SpringCloud Kubernetes不依赖bootstrap context时，可以改造移除项目中的bootstrap.yaml
 */
class ServiceBootstrapApplicationListener : ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    override fun getOrder(): Int {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1
    }

    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        with(event.environment) {
            // listen to events in a bootstrap context
            if (propertySources.contains(BOOTSTRAP_SOURCE_NAME)) {
                propertySources.addLast(createPropertySource())
            }
        }
    }

    private fun createPropertySource(): PropertiesPropertySource {
        with(Properties()) {
            if (isConsulPresent()) {
                setProperty("spring.cloud.consul.config.name", SERVICE_NAME)
                setProperty("spring.cloud.consul.config.prefix", CONSUL_CONFIG_PREFIX)
                setProperty("spring.cloud.consul.config.format", CONSUL_CONFIG_FORMAT)
                setProperty("spring.cloud.consul.config.profile-separator", CONSUL_CONFIG_SEPARATOR)
                setProperty("spring.cloud.consul.discovery.service-name", SERVICE_NAME)
            }
            if (isK8sPresent()) {
                setProperty("spring.cloud.kubernetes.config.sources[0].name", K8S_COMMON_CONFIG)
                setProperty("spring.cloud.kubernetes.config.sources[1].name", SERVICE_NAME)
            }
            return PropertiesPropertySource(DEVOPS_SOURCE_NAME, this)
        }
    }

    /**
     * 判断consul依赖是否存在
     */
    private fun isConsulPresent(): Boolean {
        return isPresent(CONSUL_CLASS_NAME)
    }

    /**
     * 判断k8s依赖是否存在
     */
    private fun isK8sPresent(): Boolean {
        return isPresent(K8S_CLASS_NAME)
    }

    /**
     * 判断类名为[className]的类是否存在
     */
    private fun isPresent(className: String): Boolean {
        return try {
            forName(className, ClassUtils.getDefaultClassLoader())
            true
        } catch (ex: Throwable) {
            false
        }
    }

    @Throws(ClassNotFoundException::class)
    private fun forName(className: String, classLoader: ClassLoader?): Class<*>? {
        return classLoader?.loadClass(className) ?: Class.forName(className)
    }

    companion object {
        private const val BOOTSTRAP_SOURCE_NAME = "bootstrap"
        private const val DEVOPS_SOURCE_NAME = "devopsProperties"
        private const val CONSUL_CONFIG_FORMAT = "YAML"
        private const val CONSUL_CONFIG_SEPARATOR = "::"
        private const val SERVICE_NAME = "\${service.prefix:}\${spring.application.name}\${service.suffix:}"
        private const val CONSUL_CONFIG_PREFIX = "\${service.prefix:}config\${service.suffix:}"
        private const val K8S_COMMON_CONFIG = "\${service.prefix:}common\${service.suffix:}"
        private const val CONSUL_CLASS_NAME = "org.springframework.cloud.consul.config.ConsulConfigAutoConfiguration"
        private const val K8S_CLASS_NAME = "org.springframework.cloud.kubernetes.commons." +
            "KubernetesCommonsAutoConfiguration"
    }
}
