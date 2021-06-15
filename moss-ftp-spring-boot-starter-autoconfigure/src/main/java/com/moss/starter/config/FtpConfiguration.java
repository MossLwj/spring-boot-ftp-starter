package com.moss.starter.config;

import com.moss.starter.propeties.FtpOptionProperties;
import com.moss.starter.service.MossFtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.io.IOException;

/**
 * @author lwj
 * @Configuration 开启配置
 * @EnableConfigurationProperties(FtpOptionProperties.class) 开启使用映射实体对象
 * @ConditionalOnClass({MossFtpService.class, GenericObjectPool.class, FTPClient.class}) 存在IotFtpService时初始化该配置类
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(FtpOptionProperties.class)
@ConditionalOnClass({MossFtpService.class, GenericObjectPool.class, FTPClient.class})
@ConditionalOnProperty(
        prefix = "moss.ftp",//存在配置前缀
        name = "enabled",
        havingValue = "true",//开启
        matchIfMissing = true//确实检查
)
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class FtpConfiguration {
    private final FtpOptionProperties ftpOptionProperties;
    private GenericObjectPool<FTPClient> pool;

    /**
     * 预先加载FTPClient连接到对象池中
     *
     * @param initialSize 初始化连接数
     * @param maxIdle     最大空闲连接数
     */
    private void preLoadingFtpClient(Integer initialSize, int maxIdle) {
        if (initialSize == null || initialSize <= 0) {
            return;
        }
        int size = Math.min(initialSize, maxIdle);
        for (int i = 0; i < size; i++) {
            try {
                pool.addObject();
            } catch (Exception e) {
                log.error("preLoadingFtpClient error...", e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        if (pool != null) {
            pool.close();
            log.info("销毁FTPClientPool...");
        }
    }


    /**
     * 根据条件判断不存在ElsService时初始化新bean到SpringIoc
     *
     * @return
     * @Bean 创建MossFtpService实体bean
     * @ConditionalOnMissingBean(MossFtpService.class) 缺失MossService实体bean时，初始化MossService并添加到Spring容器
     */
    @Bean
    @ConditionalOnMissingBean(MossFtpService.class)
    public MossFtpService mossFtpService() {
        log.info("---------------->>>The MossFtpService Not Found, Execute Creat New Bean.----------------------");
        GenericObjectPoolConfig<FTPClient> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(60000);
        poolConfig.setSoftMinEvictableIdleTimeMillis(50000);
        poolConfig.setTimeBetweenEvictionRunsMillis(30000);
        pool = new GenericObjectPool<>(new FtpClientPooledObjectFactory(ftpOptionProperties), poolConfig);
        preLoadingFtpClient(ftpOptionProperties.getInitialSize(), poolConfig.getMaxIdle());
        MossFtpService mossFtpService = new MossFtpService();
        mossFtpService.setFtpClientPool(pool);
        mossFtpService.setHasInit(true);
        log.info("---------------->>>The MossFtpService have bean build.----------------------");
        return mossFtpService;
    }

    /**
     * FtpClient对象工厂类
     */
    @Slf4j
    static class FtpClientPooledObjectFactory extends BasePooledObjectFactory<FTPClient> {
        private FtpOptionProperties props;

        FtpClientPooledObjectFactory(FtpOptionProperties props) {
            this.props = props;
        }

        @Override
        public FTPClient create() throws Exception {
            FTPClient ftpClient = new FTPClient();
            ftpClient.setConnectTimeout(props.getConnectTimeout());
            try {

                ftpClient.connect(props.getHost(), props.getPort());
                int replyCode = ftpClient.getReplyCode();
                if (!FTPReply.isPositiveCompletion(replyCode)) {
                    ftpClient.disconnect();
                    log.warn("FTPServer refused connection,replyCode:{}", replyCode);
                    return null;
                }

                if (!ftpClient.login(props.getUsername(), props.getPassword())) {
                    log.warn("ftpClient login failed... username is {}; password: {}", props.getUsername(), props.getPassword());
                }
                ftpClient.setBufferSize(props.getBufferSize());
                ftpClient.setFileType(props.getTransferFileType());
                ftpClient.setControlEncoding(props.getEncoding());
                if (props.isPassiveMode()) {
                    ftpClient.enterLocalPassiveMode();
                }
                ftpClient.setSoTimeout(props.getConnectTimeout());
            } catch (IOException e) {
                log.error("create ftp connection failed...", e);
                throw new Exception("FtpClient 创建失败", e);
            }
            return ftpClient;
        }

        /**
         * 用PooledObject封装对象放入池中
         */
        @Override
        public PooledObject<FTPClient> wrap(FTPClient ftpClient) {
            return new DefaultPooledObject<>(ftpClient);
        }

        /**
         * 销毁FtpClient对象
         */
        @Override
        public void destroyObject(PooledObject<FTPClient> ftpPooled) {
            if (ftpPooled == null) {
                return;
            }
            FTPClient ftpClient = ftpPooled.getObject();
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                }
            } catch (IOException io) {
                log.error("ftp client logout failed...{0}", io);
            } finally {
                try {
                    ftpClient.disconnect();
                } catch (IOException io) {
                    log.error("close ftp client failed...{0}", io);
                }
            }
        }

        /**
         * 验证FtpClient对象
         */
        @Override
        public boolean validateObject(PooledObject<FTPClient> ftpPooled) {
            try {
                FTPClient ftpClient = ftpPooled.getObject();
                return ftpClient.sendNoOp();
            } catch (IOException e) {
                log.error("Failed to validate client: {0}", e);
            }
            return false;
        }
    }
}
