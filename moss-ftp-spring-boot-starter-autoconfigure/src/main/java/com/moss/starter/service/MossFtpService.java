package com.moss.starter.service;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.pool2.ObjectPool;
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
    private ObjectPool<FTPClient> ftpClientPool;

    /**
     * 上传文件
     *
     * @param pathname       ftp服务保存地址
     * @param fileName       上传到ftp的文件名
     * @param originFileName 待上传文件的名称（绝对地址）
     * @return
     */
    public boolean uploadFile(String pathname, String fileName, String originFileName) {
        boolean flag = false;
        InputStream inputStream;
        FTPClient ftpClient = getFtpClient();
        try {
            log.info("-----------------------开始上传[" + fileName + "]文件！------------------------");
            inputStream = new FileInputStream(new File(originFileName));
            // 设置传输的文件类型(BINARY_FILE_TYPE：二进制文件类型 ASCII_FILE_TYPE：ASCII传输方式，这是默认的方式)
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            createDirectory(pathname, ftpClient);
            ftpClient.makeDirectory(pathname);
            flag = ftpClient.storeFile(encodingFileName(fileName), inputStream);
            inputStream.close();
            if (flag) {
                log.info("上传[" + fileName + "]文件成功！");
            } else {
                log.error("上传[" + fileName + "]文件失败！");
            }
        } catch (Exception e) {
            log.error("上传[" + fileName + "]文件失败！错误原因{}", e.getMessage());
            e.printStackTrace();
        } finally {
            releaseFtpClient(ftpClient);
        }
        return flag;
    }

    /**
     * 上传文件
     *
     * @param pathname    ftp服务保存地址
     * @param fileName    上传到ftp的文件名
     * @param inputStream 输入文件流
     * @return
     */
    public boolean uploadFile(String pathname, String fileName, InputStream inputStream) {
        boolean flag = false;
        FTPClient ftpClient = getFtpClient();
        try {
            log.info("-----------------------开始上传[" + fileName + "]文件！------------------------");
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            createDirectory(pathname, ftpClient);
            ftpClient.makeDirectory(pathname);
            ftpClient.changeWorkingDirectory(pathname);
            flag = ftpClient.storeFile(encodingFileName(fileName), inputStream);
            inputStream.close();
            if (flag) {
                log.info("上传文件[" + fileName + "]成功！");
            } else {
                log.info("上传文件[" + fileName + "]失败！");
            }
        } catch (IOException e) {
            log.info("上传文件[" + fileName + "]失败！错误原因{}", e.getMessage());
            e.printStackTrace();
        } finally {
            releaseFtpClient(ftpClient);
        }
        return flag;
    }


    /**
     * 下载文件
     *
     * @param pathname  FTP服务器文件目录
     * @param fileName  文件名称
     * @param localPath 下载后的文件路径
     * @return
     */
    public boolean downLoadFile(String pathname, String fileName, String localPath) {
        boolean flag = false;
        OutputStream os = null;
        FTPClient ftpClient = getFtpClient();
        try {
            log.info("-----------------------开始下载[" + fileName + "]文件！------------------------");
            ftpClient.changeWorkingDirectory(pathname);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            for (FTPFile ftpFile : ftpFiles) {
                if (fileName.equalsIgnoreCase(ftpFile.getName())) {
                    File localFile = new File(localPath + "/" + ftpFile.getName());
                    os = new FileOutputStream(localFile);
                    flag = ftpClient.retrieveFile(encodingFileName(fileName), os);
                    os.close();
                }
            }
            if (flag) {
                log.info("下载文件[" + fileName + "]成功！");
            } else {
                log.error("下载文件[" + fileName + "]失败！");
            }
        } catch (Exception e) {
            log.error("下载文件[" + fileName + "]失败！错误原因{}", e.getMessage());
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
     * 下载文件到Response
     *
     * @param response response
     * @param pathname FTP服务器文件目录
     * @param fileName 文件名称
     * @return
     */
    public boolean downLoadFileToResponse(HttpServletResponse response, String pathname, String fileName) {
        boolean flag = false;
        OutputStream os = null;
        FTPClient ftpClient = getFtpClient();
        InputStream inputStream;
        int len = 0;
        try {
            log.info("-----------------------开始下载[" + fileName + "]文件！------------------------");
            inputStream = ftpClient.retrieveFileStream(pathname + "/" + fileName);
            //写流文件
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[4096];
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            //写response
            if (response != null) {
                response.setContentType("application/OCTET-STREAM;charset=utf-8");
                response.setContentLength(baos.size());
                response.setHeader("Content-Disposition", "attachment;filename=\"" + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20") + "\"");
            }
            baos.writeTo(outputStream);
            outputStream.flush();
            inputStream.close();

            if (flag) {
                log.info("下载文件[" + fileName + "]成功！");
            } else {
                log.error("下载文件[" + fileName + "]失败！");
            }
        } catch (Exception e) {
            log.error("下载文件[" + fileName + "]失败！错误原因{}", e.getMessage());
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
     * 删除文件
     *
     * @param pathName
     * @param fileName
     * @return
     */
    public boolean deleteFile(String pathName, String fileName) {
        boolean flag = false;
        FTPClient ftpClient = getFtpClient();
        try {
            log.info("-----------------------开始删除[" + fileName + "]文件！------------------------");
            //  切换FTP目录
            ftpClient.changeWorkingDirectory(pathName);
            ftpClient.deleteFile(encodingFileName(fileName));
            ftpClient.logout();
            flag = true;
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
     * @return
     * @throws IOException
     */
    public List<String> readFileByLine(String remoteFilePath) throws IOException {
        FTPClient ftpClient = getFtpClient();
        try (InputStream in = ftpClient.retrieveFileStream(encodingPath(remoteFilePath));
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
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
     * @throws IOException
     */
    public FTPFile[] retrieveFTPFiles(String remotePath) throws IOException {
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
     * @throws IOException
     */
    public List<String> retrieveFileNames(String remotePath) throws IOException {
        FTPFile[] ftpFiles = retrieveFTPFiles(remotePath);
        if (ArrayUtil.isEmpty(ftpFiles)) {
            return new ArrayList<>();
        }
        return Arrays.stream(ftpFiles).filter(Objects::isNull).map(ftpFile -> ftpFile != null ? ftpFile.getName() : null).collect(Collectors.toList());
    }

    /**
     * 编码文件路径
     *
     * @param path 文件路径（path+fileName）
     * @return
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
     * @return
     * @throws UnsupportedEncodingException
     */
    private String encodingFileName(String fileName) throws UnsupportedEncodingException {
        return new String(fileName.getBytes("UTF-8"), "iso-8859-1");
    }


    /**
     * 释放ftpClient
     *
     * @param ftpClient
     */
    private void releaseFtpClient(FTPClient ftpClient) {
        if (ftpClient == null) {
            return;
        }
        try {
            ftpClientPool.returnObject(ftpClient);
        } catch (Exception e) {
            log.error("Could not return ftpClient to the pool", e);
            //  destroyFtpClient
            try {
                ftpClient.disconnect();
            } catch (IOException ioe) {

            }
        }
    }


    /**
     * 创建多层目录文件，如果有ftp服务器已存在该文件，则不创建，如果无，则创建
     *
     * @param remote
     * @param ftpClient
     * @return
     * @throws IOException
     */
    private boolean createDirectory(String remote, FTPClient ftpClient) throws IOException {
        boolean success = true;
        String directory = remote + "/";
        //  如果目录不存在，则递归创建远程服务器目录
        if (!"/".equalsIgnoreCase(directory) && !changeWorkingDirectory(new String(directory), ftpClient)) {
            int start = 0;
            int end = 0;
            if (directory.startsWith("/")) {
                start = 1;
            } else {
                start = 0;
            }
            end = directory.indexOf("/", start);
            String path = "";
            String paths = "";
            while (true) {
                String subDirectory = new String(remote.substring(start, end).getBytes("UTF-8"), "iso-8859-1");
                path = path = "/" + subDirectory;
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
                paths = paths + "/" + subDirectory;
                start = end + 1;
                end = directory.indexOf("/", start);
                //  检查所有目录是否创建完毕
                if (end <= start) {
                    break;
                }
            }
        }
        return success;
    }

    /**
     * 创建目录
     *
     * @param dir
     * @param ftpClient
     * @return
     */
    public boolean makeDirectory(String dir, FTPClient ftpClient) {
        boolean flag = true;
        try {
            flag = ftpClient.makeDirectory(dir);
            if (flag) {
                log.info("创建文件夹" + dir + " 成功！");
            } else {
                log.info("创建文件夹" + dir + " 失败！");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 判断ftp服务器文件是否存在
     *
     * @param path
     * @param ftpClient
     * @return
     */
    public boolean existFile(String path, FTPClient ftpClient) throws IOException {
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
     * @return
     */
    private FTPClient getFtpClient() {
        checkFtpClientPoolAvailable();
        FTPClient ftpClient = null;
        Exception ex = null;
        //  获取连接数最多尝试3次
        //  TODO 可将次数添加到配置中
        for (int i = 0; i < 3; i++) {
            try {
                ftpClient = ftpClientPool.borrowObject();
                //  被动模式
                ftpClient.enterLocalPassiveMode();
                ftpClient.setControlEncoding("UTF-8");
                ftpClient.changeWorkingDirectory("/");
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
     * @param directory
     * @param ftpClient
     * @return
     */
    public boolean changeWorkingDirectory(String directory, FTPClient ftpClient) {
        boolean flag = true;
        try {
            flag = ftpClient.changeWorkingDirectory(directory);
            if (flag) {
                System.out.println("进入文件夹" + directory + "成功！");
            } else {
                System.out.println("进入文件夹" + directory + "失败");
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return flag;
    }

}
