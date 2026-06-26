package com.llm.insight.service;

import com.llm.insight.dto.response.*;

import java.util.List;

public interface LogViewerService {

    LogFileDTO readLogFile(String requestId, String username, int page, int pageSize);

    List<LogSearchResultDTO> searchInLog(String requestId, String username,
                                          String keyword, boolean regex);

    String resolveLogFilePath(String requestId, String username);

    byte[] downloadLogFile(String requestId, String username);
}
