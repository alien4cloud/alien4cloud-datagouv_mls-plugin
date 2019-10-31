package org.alien4cloud.plugin.datagouv_mls.utils;

import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ConcatPropertyValue;
import org.alien4cloud.tosca.model.definitions.FunctionPropertyValue;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.normative.constants.ToscaFunctionConstants;
import org.alien4cloud.tosca.utils.InterfaceUtils;

import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.utils.PropertyUtil;

import org.alien4cloud.plugin.datagouv_mls.datastore.DataStore;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class TopologyUtils {

    public static Operation getCreateOperation(NodeTemplate nodeTemplate) {
        Operation imageOperation = InterfaceUtils.getOperationIfArtifactDefined(nodeTemplate.getInterfaces(), ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.CREATE);
        if (imageOperation != null) {
            return imageOperation;
        }
        // if not overriden in the template, fetch from the type.
        NodeType nodeType = ToscaContext.get(NodeType.class, nodeTemplate.getType());
        imageOperation = InterfaceUtils.getOperationIfArtifactDefined(nodeType.getInterfaces(), ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.CREATE);
        return imageOperation;
    }

    public static String updateInput(NodeTemplate nodeTemplate, DataStore ds, String inputName, AbstractPropertyValue iValue, 
                                     String user, String password) {
        if (iValue instanceof ConcatPropertyValue) {
            ConcatPropertyValue cpv = (ConcatPropertyValue) iValue;
            List<AbstractPropertyValue> newparms = new ArrayList<AbstractPropertyValue>();

            for (AbstractPropertyValue param : cpv.getParameters()) {
                String v = updateInput(nodeTemplate, ds, inputName, param,  user, password);

                if (v == null) {
                   newparms.add(param);
                } else {
                   newparms.add(new ScalarPropertyValue(v));
                }
            }

            cpv.setParameters (newparms);

            return null;
        }
        if (iValue instanceof FunctionPropertyValue && ((FunctionPropertyValue)iValue).getTemplateName().endsWith(ToscaFunctionConstants.TARGET)) {
            FunctionPropertyValue fpv = (FunctionPropertyValue)iValue;

            return ds.updateInput (fpv.getFunction(), fpv.getParameters(), user, password);
        }
        return null;
    }

}
