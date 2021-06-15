package com.moss.starter.propeties;

import lombok.Data;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Ftp配置类
 *
 * @author lwj
 */
@Data
@EnableConfigurationProperties
@Component
@ConfigurationProperties(prefix = "moss.ftp")
public class FtpOptionProperties {

    /**
     * 是否开启ftpClient,默认为true
     **/
    private boolean isOpen = true;
    /**
     * 连接ftp的IP
     **/
    private String host;
    /**
     * 连接ftp的端口（默认为21）
     **/
    private int port = FTPClient.DEFAULT_PORT;
    /**
     * 连接ftp的用户名
     **/
    private String username;
    /**
     * 连接ftp的密码
     **/
    private String password;
    /**
     * 缓冲区大小（默认1024）
     **/
    private int bufferSize = 1024;
    /**
     * 初始化大小
     **/
    private Integer initialSize = 0;
    /**
     * 编码格式（默认为UTF-8）
     **/
    private String encoding = StandardCharsets.UTF_8.toString();
    /**
     * 获取ftpClient的尝试数（默认为3）
     **/
    private Integer tryNum = 3;
    /**
     * 连接超时时间(秒)
     **/
    private Integer connectTimeout = 30000;
    /**
     * 传输文件类型
     **/
    private Integer transferFileType = FTP.BINARY_FILE_TYPE;
    /**
     * 被动模式
     **/
    private boolean passiveMode = true;
    /**
     * 存储空间名称
     **/
    private String bucketName;

}
