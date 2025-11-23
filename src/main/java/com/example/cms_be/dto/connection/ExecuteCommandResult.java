package com.example.cms_be.dto.connection;

import lombok.Data;

@Data
public  class ExecuteCommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final String combinedOutput;
        
        public ExecuteCommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.combinedOutput = stdout + (stderr.isEmpty() ? "" : "\n" + stderr);
        }
        
       
    }