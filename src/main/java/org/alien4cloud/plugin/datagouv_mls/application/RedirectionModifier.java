package org.alien4cloud.plugin.datagouv_mls.application;

import alien4cloud.topology.task.RedirectionTask;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.Topology;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component("datagouv_mls-redirection-sample")
@Slf4j
public class RedirectionModifier extends TopologyModifierSupport {

    private Random random = new Random();

    private static int count = 0;

    @Override
    public void process(Topology topology, FlowExecutionContext context) {
        count++;

        if ((count % 2) == 1) {
           return;
        }
        // Let's randomly generate a DuckDuckGo URL searching images for a randomly generated number
        String url = String.format("https://duckduckgo.com/?q=%d&iar=images&iax=images&ia=images", random.nextInt(50));
        // This warn will be used in legacy A4C UI
        context.log().warn("Please use the following URL to continue : " + url);
        // This error will stop the deployment flow and trigger an URL redirection in Wizard
        context.log().error(new RedirectionTask(url, "urlRetour"));
    }
}
