package com.moss.starter.service;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.moss.starter.dto.FtpFileDto;
import com.moss.starter.propeties.FtpOptionProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ftp服务端
 *
 * @author lwj
 * @date 2019/7/23
 */
@Data
@Slf4j
public class MossFtpService {

    /**
     * ftpClient连接池初始化标识
     */
    private boolean hasInit = false;

    /**
     * ftpClient连接池
     */
    private GenericObjectPool<FTPClient> ftpClientPool;

    @Autowired
    private HttpServletResponse response;
    @Autowired
    private FtpOptionProperties ftpOptionProperties;

    /**
     * 上传文件（根据文件绝对路径上传）
     *
     * @param pathName       ftp服务保存地址
     * @param fileName       上传到ftp的文件名
     * @param originFileName 待上传文件的名称（绝对地址）
     * @return true：成功；false：失败
     */
    public boolean uploadFile(String pathName, String fileName, String originFileName) {
        boolean flag;
        InputStream inputStream;
        FTPClient ftpClient = null;
        try {
            ftpClient = getFtpClient();
            log.info("-----------------------开始上传[" + fileName + "]文件！------------------------");
            inputStream = new FileInputStream(new File(originFileName));
            // 设置传输的文件类型(BINARY_FILE_TYPE：二进制文件类型 ASCII_FILE_TYPE：ASCII传输方式，这是默认的方式)
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            createDirectory(pathName, ftpClient);
            ftpClient.makeDirectory(pathName);
            flag = ftpClient.storeFile(encodingFileName(fileName), inputStream);
            inputStream.close();
        } catch (Exception e) {
            flag = false;
            log.error("-----------------------上传[" + fileName + "]文件失败！错误原因{}-----------------------", e.getMessage());
            e.printStackTrace();
        } finally {
            releaseFtpClient(ftpClient);
        }
        if (flag) {
            log.info("-----------------------上传[" + fileName + "]文件成功！-----------------------");
        }
        return flag;
    }

    /**
     * 上传文件（传入文件流的方式上传）
     *
     * @param pathName    ftp服务保存地址
     * @param fileName    上传到ftp的文件名
     * @param inputStream 输入文件流
     * @return true：成功；false：失败
     */
    public boolean uploadFile(String pathName, String fileName, InputStream inputStream) {
        boolean flag;
        FTPClient ftpClient = null;
        try {
            ftpClient = getFtpClient();
            log.info("-----------------------开始上传[" + fileName + "]文件！------------------------");
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            createDirectory(pathName, ftpClient);
            ftpClient.makeDirectory(pathName);
            ftpClient.changeWorkingDirectory(pathName);
            flag = ftpClient.storeFile(encodingFileName(fileName), inputStream);

        } catch (Exception e) {
            flag = false;
            log.info("-----------------------上传文件[" + fileName + "]失败！错误原因{}-----------------------", e.getMessage());
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(inputStream);
            releaseFtpClient(ftpClient);
        }
        if (flag) {
            log.info("-----------------------上传文件[" + fileName + "]成功！-----------------------");
        }
        return flag;
    }

    /**
     * 下载文件（下载到本地的某个位置）
     *
     * @param pathName  FTP服务器文件目录
     * @param fileName  文件名称
     * @param localPath 下载后的文件路径
     * @return true：成功；false：失败
     */
    public boolean downLoadFile(String pathName, String fileName, String localPath) {
        boolean flag = true;
        OutputStream os = null;
        FTPClient ftpClient = null;
        try {
            ftpClient = getFtpClient();
            log.info("-----------------------开始下载[" + fileName + "]文件！------------------------");
            ftpClient.changeWorkingDirectory(pathName);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            for (FTPFile ftpFile : ftpFiles) {
                if (fileName.equalsIgnoreCase(ftpFile.getName())) {
                    File localFile = new File(localPath + "/" + ftpFile.getName());
                    os = new FileOutputStream(localFile);
                    flag = ftpClient.retrieveFile(encodingFileName(fileName), os);
                    os.close();
                }
            }
        } catch (Exception e) {
            flag = false;
            log.error("-----------------------下载文件[" + fileName + "]失败！错误原因{}-----------------------", e.getMessage());
            e.printStackTrace();
        } finally {
            releaseFtpClient(ftpClient);
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
        return flag;
    }

    /**
     * 下载文件(文件流形式)
     *
     * @param pathName 文件的相对地址
     * @param fileName 文件真实名称
     * @return 文件流
     */
    public InputStream downLoadFileToStream(String pathName, String fileName) throws Exception {
        FTPClient ftpClient = ftpClientPool.borrowObject();
        InputStream stream1 = null;
        try (InputStream inputStream = ftpClient.retrieveFileStream(pathName);) {
            log.info("-----------------------开始下载[" + fileName + "]文件！------------------------");
            ftpClient.enterLocalPassiveMode();
            ByteArrayOutputStream baos = cloneInputStream(inputStream);
            stream1 = new ByteArrayInputStream(baos.toByteArray());
            //  关闭字节流
            IOUtils.closeQuietly(baos);
            log.info("------------------reply-------------{}", ftpClient.getReplyCode());
        } catch (Exception e) {
            log.error("-----------------------获取文件流[" + fileName + "]失败！错误原因{}-----------------------", e.getMessage());
            e.printStackTrace();
        } finally {
            ftpClient.completePendingCommand();
            releaseFtpClient(ftpClient);
        }
        return stream1;
    }

    /**
     * 下载文件到Response
     *
     * @param pathName FTP服务器文件的相对地址
     * @param fileName 文件真实名称
     * @return true：成功；false：失败
     */
    public boolean downLoadFileToResponse(String pathName, String fileName) throws Exception {
        boolean flag = true;
        InputStream inputStream = null;
        FTPClient ftpClient = ftpClientPool.borrowObject();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (response != null) {
            try (OutputStream os = response.getOutputStream()) {
                log.info("-----------------------开始下载[" + fileName + "]文件！------------------------");
                ftpClient.enterLocalPassiveMode();
                inputStream = ftpClient.retrieveFileStream(pathName);
                //  为了返回文件流到http响应中，此处不能关闭文件流
//                inputStream.close();
//                ftpClient.completePendingCommand();

                //写流文件
                byte[] buffer = new byte[ftpClient.getBufferSize()];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                //写response
                response.setContentType("application/OCTET-STREAM;charset=utf-8");
                response.setContentLength(baos.size());
                response.setHeader("Content-Disposition", "attachment;filename=\"" + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20") + "\"");
                baos.writeTo(os);
                os.flush();
                log.info("------------------reply-------------{}", ftpClient.getReply());
            } catch (Exception e) {
                flag = false;
                log.error("-----------------------下载文件[" + fileName + "]失败！错误原因{}-----------------------", e.getMessage());
                e.printStackTrace();
            } finally {
                IOUtils.closeQuietly(inputStream);
                IOUtils.closeQuietly(baos);
                releaseFtpClient(ftpClient);
                if (flag) {
                    log.info("-----------------------下载文件[" + fileName + "]成功！-----------------------");
                } else {
                    log.error("-----------------------下载文件[" + fileName + "]失败！错误原因{}-----------------------", "response is null");
                }
            }
        } else {
            log.error("-----------------------下载文件[" + fileName + "]失败！错误原因{}-----------------------", "response is null");
            return false;
        }
        return flag;
    }

    /**
     * 打包下载文件到Response
     *
     * @param fileDtoS 需要打包的一组文件
     * @param zipName  zip打包的真实名称
     * @return true：成功；false：失败
     */
    public boolean downLoadFileByZipToResponse(List<FtpFileDto> fileDtoS, String zipName) {
        boolean flag = true;
        FTPClient ftpClient = getFtpClient();
        try (OutputStream outputStream = response.getOutputStream(); ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            response.setContentType("application/OCTET-STREAM;charset=utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=\"" + URLEncoder.encode(zipName, "UTF-8").replace("+", "%20") + "\"");
            for (FtpFileDto ftpFileDto : fileDtoS) {
                int len = 0;
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); InputStream in = ftpClient.retrieveFileStream(ftpFileDto.getPath())) {
                    byte[] buffer = new byte[4096];
                    while ((len = in.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    zipOutputStream.putNextEntry(new ZipEntry(ftpFileDto.getFileName()));
                    zipOutputStream.write(baos.toByteArray(), 0, baos.toByteArray().length);
                    log.info("------------------reply-------------{}", ftpClient.getReply());
                } catch (Exception ex) {
                    log.error("-----------------------下载文件[" + ftpFileDto.getFileName() + "]失败！错误原因{}-----------------------", ex.getMessage());
                }
            }
            zipOutputStream.flush();
            response.flushBuffer();
        } catch (Exception e) {
            flag = false;
            log.error("-----------------------下载Zip文件[" + zipName + "]失败！错误原因{}-----------------------", e.getMessage());
            e.printStackTrace();
        } finally {
            releaseFtpClient(ftpClient);
        }
        if (flag) {
            log.info("-----------------------下载Zip文件[" + zipName + "]成功！-----------------------");
        }
        return flag;
    }

    /**
     * 删除文件
     *
     * @param path     文件路径 /dris/20210611/2b013e3b-cc68-474d-8dc4-1d4163308f9e.jpg
     * @param fileName 文件名 avator.jpg
     * @return true：成功；false：失败
     */
    public boolean deleteFile(String path, String fileName) {
        boolean flag = false;
        FTPClient ftpClient = null;
        try {
            ftpClient = getFtpClient();
            log.info("-----------------------开始删除[" + fileName + "]文件！------------------------");
            //  从path中截取中间的文件夹路径
            final String absolutePath = path.trim().substring(0, path.trim().lastIndexOf('/') + 1);
            //  获取文件的uuid名称
            final String fileUniqueId = path.trim().substring(path.trim().lastIndexOf('/') + 1);
            //  切换到文件在FTP服务器上的目录
            ftpClient.changeWorkingDirectory(absolutePath);
            //  通过文件名删除文件
            boolean delFlag = ftpClient.deleteFile(encodingFileName(fileUniqueId));
            if (!delFlag) {
                log.info("【文件删除】删除文件失败，文件名={}", fileName);
            }
            flag = delFlag;
            log.info("-----------------------删除文件[" + fileName + "]成功！------------------------");
        } catch (Exception e) {
            log.error("-----------------------删除文件[" + fileName + "]失败！------------------------");
            e.printStackTrace();
        } finally {
            releaseFtpClient(ftpClient);
        }
        return flag;
    }

    /**
     * 按行读取FTP文件
     *
     * @param remoteFilePath 文件路径（path+fileName）
     * @return 文件名列表
     */
    public List<String> readFileByLine(String remoteFilePath) throws IOException {
        FTPClient ftpClient = getFtpClient();
        try (InputStream in = ftpClient.retrieveFileStream(encodingPath(remoteFilePath));
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return br.lines().map(StrUtil::trimToEmpty)
                    .filter(StrUtil::isNotEmpty).collect(Collectors.toList());
        } finally {
            ftpClient.completePendingCommand();
            releaseFtpClient(ftpClient);
        }
    }

    /**
     * 获取指定路径下FTP文件
     *
     * @param remotePath 路径
     * @return FTPFile数组
     */
    public FTPFile[] retrieveFtpFiles(String remotePath) throws IOException {
        FTPClient ftpClient = getFtpClient();
        try {
            return ftpClient.listFiles(encodingPath(remotePath + "/"), file -> file != null && file.getSize() > 0);
        } finally {
            releaseFtpClient(ftpClient);
        }
    }

    /**
     * 获取指定路径下FTP文件名称
     *
     * @param remotePath 路径
     * @return ftp文件名称列表
     */
    public List<String> retrieveFileNames(String remotePath) throws IOException {
        FTPFile[] ftpFiles = retrieveFtpFiles(remotePath);
        if (ArrayUtil.isEmpty(ftpFiles)) {
            return new ArrayList<>();
        }
        return Arrays.stream(ftpFiles).filter(Objects::isNull)
                .map(ftpFile -> ftpFile != null ? ftpFile.getName() : null).collect(Collectors.toList());
    }

    /**
     * 编码文件路径
     *
     * @param path 文件路径（path+fileName）
     * @return 编码后的文件路径
     * @throws UnsupportedEncodingException
     */
    private String encodingPath(String path) throws UnsupportedEncodingException {
        // FTP协议里面，规定文件名编码为iso-8859-1，所以目录名或文件名需要转码
        return new String(path.replaceAll("//", "/").getBytes("GBK"), StandardCharsets.ISO_8859_1);
    }

    /**
     * 编码附件名称（由于中文名称时会导致上传下载失败）
     *
     * @param fileName 附件名称
     * @return 编码后的文件名
     */
    private String encodingFileName(String fileName) {
        return new String(fileName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    /**
     * 释放ftpClient
     *
     * @param ftpClient 使用的ftpClient
     */
    private void releaseFtpClient(FTPClient ftpClient) {
        if (ftpClient == null) {
            return;
        }
        try {
            //  切换文件地址
            boolean flag = this.changeWorkingDirectory("/", ftpClient);
            if (!flag) {
                ftpClient.disconnect();
                log.info("将FtpClient放回到pool中时，重置FtpClient所在文件夹失败");
            } else {
                ftpClientPool.returnObject(ftpClient);
            }
        } catch (Exception e) {
            log.error("Could not return ftpClient to the pool", e);
            //  destroyFtpClient
            try {
                ftpClient.disconnect();
            } catch (IOException ioe) {
                log.error("Could not disconnect ftpClient");
            }
        }
    }

    /**
     * 创建多层目录文件，如果有ftp服务器已存在该文件，则不创建，如果无，则创建
     *
     * @param remote    ftp路径
     * @param ftpClient 当前获取到的ftpClient
     */
    private void createDirectory(String remote, FTPClient ftpClient) throws IOException {
        String directory = remote + "/";
        //  如果目录不存在，则递归创建远程服务器目录
        if (!"/".equalsIgnoreCase(directory) && !changeWorkingDirectory(encodingPath(directory), ftpClient)) {
            int start = 0;
            int end = 0;
            if (directory.startsWith("/")) {
                start = 1;
            }
            end = directory.indexOf("/", start);
            String path = "";
            StringBuilder paths = new StringBuilder();
            do {
                String subDirectory = new String(remote.substring(start, end).getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
                path = "/" + subDirectory;
                if (!existFile(path, ftpClient)) {
                    if (makeDirectory(subDirectory, ftpClient)) {
                        changeWorkingDirectory(subDirectory, ftpClient);
                    } else {
                        System.out.println("创建目录[" + subDirectory + "]失败！");
                        changeWorkingDirectory(subDirectory, ftpClient);
                    }
                } else {
                    changeWorkingDirectory(subDirectory, ftpClient);
                }
                paths.append("/").append(subDirectory);
                start = end + 1;
                end = directory.indexOf("/", start);
                //  检查所有目录是否创建完毕
            } while (end > start);
        }
    }

    /**
     * 创建目录
     *
     * @param dir       目录
     * @param ftpClient 当前获取到的ftpClient
     * @return true：成功；false：失败
     */
    private boolean makeDirectory(String dir, FTPClient ftpClient) {
        boolean flag = true;
        try {
            flag = ftpClient.makeDirectory(dir);
            if (flag) {
                log.info("-----------------------创建文件夹" + dir + " 成功！-----------------------");
            } else {
                log.info("-----------------------创建文件夹" + dir + " 失败！-----------------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 判断ftp服务器文件是否存在
     *
     * @param path      文件路径
     * @param ftpClient 当前获取到的ftpClient
     * @return true：成功；false：失败
     */
    private boolean existFile(String path, FTPClient ftpClient) throws IOException {
        boolean flag = false;
        FTPFile[] ftpFileArr = ftpClient.listFiles(path);
        if (ftpFileArr.length > 0) {
            flag = true;
        }
        return flag;
    }


    /**
     * 获取ftpClient
     *
     * @return ftpClient
     */
    private FTPClient getFtpClient() {
        checkFtpClientPoolAvailable();
        FTPClient ftpClient = null;
        Exception ex = null;
        //  获取连接数默认尝试3次
        for (int i = 0; i < ftpOptionProperties.getTryNum(); i++) {
            try {
                ftpClient = ftpClientPool.borrowObject();
                break;
            } catch (Exception e) {
                ex = e;
            }
        }
        if (ftpClient == null) {
            throw new RuntimeException("Could not get a ftpClient from the pool", ex);
        }
        return ftpClient;
    }

    /**
     * 检查ftpClientPool是否可用
     */
    private void checkFtpClientPoolAvailable() {
        Assert.state(hasInit, "FTP未启用或连接失败！");
    }

    /**
     * 改变目录路径
     *
     * @param directory 切换的文件夹路径
     * @param ftpClient ftpClient
     * @return true：成功；false：失败
     */
    private boolean changeWorkingDirectory(String directory, FTPClient ftpClient) {
        boolean flag = true;
        try {
            flag = ftpClient.changeWorkingDirectory(new String(directory.getBytes(), FTP.DEFAULT_CONTROL_ENCODING));
            if (flag) {
                System.out.println("-----------------------进入文件夹[ " + directory + " ]成功！-----------------------");
                System.out.println("-----------------------进入文件夹[ " + ftpClient.printWorkingDirectory() + " ]成功！-----------------------");
            } else {
                System.out.println("-----------------------进入文件夹[ " + directory + " ]失败-----------------------");
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return flag;
    }

    /**
     * 关闭文件流
     *
     * @param input 文件流
     */
    private static ByteArrayOutputStream cloneInputStream(InputStream input) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = input.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            return baos;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
