package org.alien4cloud.plugin.datagouv_mls.csar;

import alien4cloud.plugin.archives.AbstractPluginArchiveService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PluginArchiveService extends AbstractPluginArchiveService  {

    @Override
    protected Logger getLogger() {
        return log;
    }
}
