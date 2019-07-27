package com.moss.starter.propeties;

import lombok.Data;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Ftp配置类
 * @author lwj
 */
@Data
@EnableConfigurationProperties
@Component
@ConfigurationProperties(prefix = "moss-ftp")
public class FtpOptionProperties {

    private boolean isOpen;

    private String host;

    private int port = FTPClient.DEFAULT_PORT;

    private String username;

    private String password;

    private int bufferSize = 8096;

    private Integer initialSize = 0;

    private String encoding = StandardCharsets.UTF_8.toString();

}
