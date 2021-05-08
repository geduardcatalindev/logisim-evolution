/*
 * This file is part of logisim-evolution.
 *
 * Logisim-evolution is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Logisim-evolution is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with logisim-evolution. If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code by Carl Burch (http://www.cburch.com), 2011.
 * Subsequent modifications by:
 *   + College of the Holy Cross
 *     http://www.holycross.edu
 *   + Haute École Spécialisée Bernoise/Berner Fachhochschule
 *     http://www.bfh.ch
 *   + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *     http://hepia.hesge.ch/
 *   + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *     http://www.heig-vd.ch/
 */

package com.cburch.logisim.vhdl.sim;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.std.hdl.VhdlEntityComponent;
import com.cburch.logisim.util.FileUtil;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.vhdl.base.VhdlEntity;
import com.cburch.logisim.vhdl.base.VhdlEntityAttributes;
import com.cburch.logisim.vhdl.base.VhdlParser;
import com.cburch.logisim.vhdl.base.VhdlSimConstants;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a simulation top file. This file contains all the interfaces to the entities (in and
 * out pins) so the simulation is run on a single top component. It allows us to have only one
 * instance of Questasim running.
 *
 * @author christian.mueller@heig-vd.ch
 */
public class VhdlSimulatorVhdlTop {

  static final Logger logger = LoggerFactory.getLogger(VhdlSimulatorVhdlTop.class);

  private boolean valid = false;
  private final VhdlSimulatorTop vhdlSimulator;
  private boolean firstPort;
  private boolean firstComp;
  private boolean firstMap;

  VhdlSimulatorVhdlTop(VhdlSimulatorTop vs) {
    vhdlSimulator = vs;
  }

  public void fireInvalidated() {
    valid = false;
  }

  public void generate(List<Component> comps) {

    /* Do not generate if file is already valid */
    if (valid) return;

    StringBuilder ports = new StringBuilder();
    ports.append("Autogenerated by logisim --");
    ports.append(System.getProperty("line.separator"));

    StringBuilder components = new StringBuilder();
    components.append("Autogenerated by logisim --");
    components.append(System.getProperty("line.separator"));

    StringBuilder map = new StringBuilder();
    map.append("Autogenerated by logisim --");
    map.append(System.getProperty("line.separator"));

    firstPort = firstComp = firstMap = true;
    String[] type = {Port.INOUT, Port.INPUT, Port.OUTPUT};

    /* For each vhdl entity */
    for (Component comp : comps) {
      InstanceState state = vhdlSimulator.getProject().getCircuitState().getInstanceState(comp);

      ComponentFactory fac = comp.getFactory();
      String vhdlEntityName;
      List<VhdlParser.PortDescription> MyPorts = new ArrayList<>();
      if (fac instanceof VhdlEntity) {
        vhdlEntityName = ((VhdlEntity) fac).GetSimName(state.getInstance().getAttributeSet());
        MyPorts.addAll(((VhdlEntityAttributes) state.getAttributeSet()).getContent().getPorts());
      } else {
        vhdlEntityName =
            ((VhdlEntityComponent) fac).GetSimName(state.getInstance().getAttributeSet());
        for (Port port :
            state.getAttributeValue(VhdlEntityComponent.CONTENT_ATTR)
                .getPorts()) {
          VhdlParser.PortDescription nport =
              new VhdlParser.PortDescription(
                  port.getToolTip(), type[port.getType()], port.getFixedBitWidth().getWidth());
          MyPorts.add(nport);
        }
      }

      /*
       * Create ports
       */
      for (VhdlParser.PortDescription port : MyPorts) {
        if (!firstPort) {
          ports.append(";");
          ports.append(System.getProperty("line.separator"));
        } else {
          firstPort = false;
        }
        String portName = vhdlEntityName + "_" + port.getName();
        ports.append("		").append(portName).append(" : ").append(port.getVhdlType())
            .append(" std_logic");
        int width = port.getWidth().getWidth();
        if (width > 1) {
          ports.append("_vector(").append(width - 1).append(" downto 0)");
        }
      }

      /*
       * Create components
       */
      components.append("	component ").append(vhdlEntityName);
      components.append(System.getProperty("line.separator"));

      components.append("		port (");
      components.append(System.getProperty("line.separator"));

      firstComp = true;
      for (VhdlParser.PortDescription port : MyPorts) {
        if (!firstComp) {
          components.append(";");
          components.append(System.getProperty("line.separator"));
        } else firstComp = false;

        components.append("			").append(port.getName()).append(" : ").append(port.getVhdlType())
            .append(" std_logic");

        int width = port.getWidth().getWidth();
        if (width > 1) {
          components.append("_vector(").append(width - 1).append(" downto 0)");
        }
      }

      components.append(System.getProperty("line.separator"));
      components.append("		);");
      components.append(System.getProperty("line.separator"));

      components.append("	end component ;");
      components.append(System.getProperty("line.separator"));

      components.append("	");
      components.append(System.getProperty("line.separator"));

      /*
       * Create port map
       */
      map.append("	").append(vhdlEntityName).append("_map : ").append(vhdlEntityName)
          .append(" port map (");
      map.append(System.getProperty("line.separator"));

      firstMap = true;
      for (VhdlParser.PortDescription port : MyPorts) {

        if (!firstMap) {
          map.append(",");
          map.append(System.getProperty("line.separator"));
        } else firstMap = false;

        map.append("		").append(port.getName()).append(" => ").append(vhdlEntityName).append("_")
            .append(port.getName());
      }
      map.append(System.getProperty("line.separator"));
      map.append("	);");
      map.append(System.getProperty("line.separator"));
      map.append("	");
      map.append(System.getProperty("line.separator"));
    }

    ports.append(System.getProperty("line.separator"));
    ports.append("		---------------------------");
    ports.append(System.getProperty("line.separator"));

    components.append("	---------------------------");
    components.append(System.getProperty("line.separator"));

    map.append("	---------------------------");
    map.append(System.getProperty("line.separator"));

    /*
     * Replace template blocks by generated datas
     */
    String template;
    try {
      template =
          new String(
              FileUtil.getBytes(
                  this.getClass()
                      .getResourceAsStream(
                          VhdlSimConstants.VHDL_TEMPLATES_PATH + "top_sim.templ")));
    } catch (IOException e) {
      logger.error("Could not read template : {}", e.getMessage());
      return;
    }

    template = template.replaceAll("%date%", LocaleManager.parserSDF.format(new Date()));
    template = template.replaceAll("%ports%", ports.toString());
    template = template.replaceAll("%components%", components.toString());
    template = template.replaceAll("%map%", map.toString());

    PrintWriter writer;
    try {
      writer =
          new PrintWriter(
              VhdlSimConstants.SIM_SRC_PATH + VhdlSimConstants.SIM_TOP_FILENAME,
              StandardCharsets.UTF_8);
      writer.print(template);
      writer.close();
    } catch (IOException e) {
      logger.error("Could not create top_sim file : {}", e.getMessage());
      e.printStackTrace();
      return;
    }

    valid = true;
  }
}
