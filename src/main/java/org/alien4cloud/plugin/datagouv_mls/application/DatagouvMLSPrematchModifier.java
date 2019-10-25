package org.alien4cloud.plugin.datagouv_mls.application;

import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.tosca.context.ToscaContextual;
import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.CloneUtil;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component("datagouv_mls-pre_match-modifier")
public class DatagouvMLSPrematchModifier extends TopologyModifierSupport {

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        log.info("Processing topology " + topology.getId());

        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);
        } catch (Exception e) {
            context.getLog().error("Couldn't process Datagouv-MLS modifier");
            log.warn ("Couldn't process Datagouv-MLS modifier, got " + e.getMessage());
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    private void doProcess(Topology topology, FlowExecutionContext context) {

       /* nodes to be inserted */
       Map<String, NodeTemplate> newNodes = new HashMap<String, NodeTemplate>();
       /* relations to be updated */
       Map<String, RelationshipTemplate> modRelations = new HashMap<String, RelationshipTemplate>();
       /* nodes to be removed */
       Set<NodeTemplate> toBeRemoved = new HashSet<NodeTemplate>();
       
       /* process nodes */
       Map<String, NodeTemplate> nodeTemplates = topology.getNodeTemplates();
       for (String nodeName : nodeTemplates.keySet()) {
          /* nodes to be added contain a "container" property */
          if ( safe(nodeTemplates.get(nodeName).getProperties()).get("container") != null) {

             Map<String, RelationshipTemplate> relationships = nodeTemplates.get(nodeName).getRelationships();
             for (String nrel : safe(relationships).keySet()) {
                 /* datastores relations are in dataStoreTypes map */
                 if (DatagouvMLSConstants.dataStoreTypes.keySet().contains(relationships.get(nrel).getRequirementType())) {

                    /* get original datastore node */
                    NodeTemplate dsNode = nodeTemplates.get(relationships.get(nrel).getTarget());
                    toBeRemoved.add(dsNode);

                    NodeTemplate newNode = CloneUtil.clone(dsNode);
                    String newName = newNode.getName() + "-" + nodeName;
                    newNode.setName(newName);
                    newNodes.put (newName, newNode);
                    modRelations.put (newName, relationships.get(nrel));

                 }
             }
          }
       }

       /* add datastore nodes for each module */
       nodeTemplates.putAll(newNodes);
       /* update relations targets */
       for (String target : modRelations.keySet()) {
          modRelations.get(target).setTarget(target);
       }
       /* remove original datastore nodes */
       for (NodeTemplate dsNode : toBeRemoved) {
          removeNode (topology, dsNode);
       }

    }
}
