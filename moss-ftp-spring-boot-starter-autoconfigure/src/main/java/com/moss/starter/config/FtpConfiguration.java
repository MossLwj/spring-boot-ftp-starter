package com.moss.starter.config;

import com.moss.starter.propeties.FtpOptionProperties;
import com.moss.starter.service.MossFtpService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
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

/**
 *
 * @Configuration 开启配置
 * @EnableConfigurationProperties(FtpOptionProperties.class) 开启使用映射实体对象
 * @ConditionalOnClass({MossFtpService.class, GenericObjectPool.class, FTPClient.class}) 存在IotFtpService时初始化该配置类
 * @author lwj
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(FtpOptionProperties.class)
@ConditionalOnClass({MossFtpService.class, GenericObjectPool.class, FTPClient.class})
@ConditionalOnProperty(
        prefix = "moss-ftp",//存在配置前缀
        name="enabled",
        havingValue = "true",//开启
        matchIfMissing = true//确实检查

)
public class FtpConfiguration {
    @Autowired
    private FtpOptionProperties ftpOptionProperties;

    private GenericObjectPool pool;

    /**
     * 预先加载FTPClient连接到对象池中
     * @param initialSize   初始化连接数
     * @param maxIdle   最大空闲连接数
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
     * @Bean 创建MossFtpService实体bean
     * @ConditionalOnMissingBean(MossFtpService.class) 缺失MossService实体bean时，初始化MossService并添加到Spring容器
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(MossFtpService.class)
    public MossFtpService mossFtpService() {
        log.info("---------------->>>The MossFtpService Not Found, Execute Creat New Bean.----------------------");
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
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
    static class FtpClientPooledObjectFactory implements PooledObjectFactory<FTPClient> {
        private FtpOptionProperties props;

        FtpClientPooledObjectFactory(FtpOptionProperties props) {
            this.props = props;
        }

        @Override
        public PooledObject<FTPClient> makeObject() throws Exception {
            FTPClient ftpClient = new FTPClient();
            try {
                ftpClient.connect(props.getHost(), props.getPort());
                ftpClient.login(props.getUsername(), props.getPassword());
                log.info("连接FTP服务器返回码{}", ftpClient.getReplyCode());
                ftpClient.setBufferSize(props.getBufferSize());
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                ftpClient.enterLocalPassiveMode();
                return new DefaultPooledObject<>(ftpClient);
            } catch (Exception e) {
                log.error("建立FTP连接失败", e);
                if (ftpClient.isAvailable()) {
                    ftpClient.disconnect();
                }
                ftpClient = null;
                throw new Exception("建立FTP连接失败", e);
            }
        }

        @Override
        public void destroyObject(PooledObject<FTPClient> pooledObject) throws Exception {
            FTPClient ftpClient = getObject(pooledObject);
            if (ftpClient != null && ftpClient.isConnected()) {
                ftpClient.disconnect();
            }
        }

        @Override
        public boolean validateObject(PooledObject<FTPClient> pooledObject) {
            FTPClient ftpClient = getObject(pooledObject);
            if (ftpClient == null || !ftpClient.isConnected()) {
                return false;
            }
            try {
                ftpClient.changeWorkingDirectory("/");
                return true;
            } catch (Exception e) {
                log.error("验证FTP连接失败::{}", e.getMessage());
                return false;
            }
        }

        @Override
        public void activateObject(PooledObject<FTPClient> pooledObject) throws Exception {

        }

        @Override
        public void passivateObject(PooledObject<FTPClient> pooledObject) throws Exception {

        }

        private FTPClient getObject(PooledObject<FTPClient> pooledObject) {
            if (pooledObject == null || pooledObject.getObject() == null) {
                return null;
            }
            return pooledObject.getObject();
        }
    }
}
