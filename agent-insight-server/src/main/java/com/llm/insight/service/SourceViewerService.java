package com.llm.insight.service;

import com.llm.insight.dto.response.*;

import java.util.List;

public interface SourceViewerService {

    List<ScriptFileDTO> listScripts(String agentName);

    String getScriptContent(String scriptPath, Integer startLine, Integer endLine);

    SourceLineMappingDTO mapTaskToLine(String agentName, String taskUniqueName);
}
