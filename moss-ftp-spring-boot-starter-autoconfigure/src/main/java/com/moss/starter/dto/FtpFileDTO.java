package com.moss.starter.dto;

import lombok.Data;

/**
 * @author lwj
 */
@Data
public class FtpFileDTO {

    /** 附件ftp路径 */
    private String path;
    /** 附件名称 */
    private String fileName;
}
