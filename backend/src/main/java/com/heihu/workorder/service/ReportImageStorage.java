package com.heihu.workorder.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface ReportImageStorage {

    void store(String storedFileName, MultipartFile file);

    Resource load(String storedFileName);

    boolean delete(String storedFileName);
}
