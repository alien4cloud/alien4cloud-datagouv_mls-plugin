package org.alien4cloud.plugin.datagouv_mls.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class Message {
   Entities entities;
   String user = "a4c";
   String type;
}
