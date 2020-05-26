package com.moss.starter.propeties;

import lombok.Data;

/**
 * @description 文件路径配置类
 * @author: lwj
 * @create: 2020-05-14 15:49
 **/
@Data
public class DirProperties {

    /** 项目文件上传的指定文件夹地址 **/
    private String prefix;
    /** 项目文件预览文件存放的地址 **/
    private String preview;

}
