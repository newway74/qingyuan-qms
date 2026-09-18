package com.qms.framework.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 单机离线版：由后端同端口托管 React 前端静态资源（替代 nginx）。
 * 静态目录默认取 jar 同级 ./static，可通过 qms.web.static-dir 覆盖。
 * 非 API、且磁盘上找不到对应文件的路径（如前端路由 /login 刷新）统一回退 index.html。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Value("${qms.web.static-dir:./static}")
    private String staticDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = staticDir.endsWith("/") ? staticDir : staticDir + "/";
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + location)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 接口、swagger、actuator、带后缀的文件不做 SPA 回退
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("actuator/")
                                || resourcePath.startsWith("swagger-ui")
                                || resourcePath.startsWith("v3/api-docs")
                                || resourcePath.startsWith("webjars/")
                                || resourcePath.contains(".")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.exists() ? index : new ClassPathResource("/static/index.html");
                    }
                });
    }
}
