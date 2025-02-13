/*******************************************************************************
 * Copyright 2018 572682
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package us.dot.its.jpo.ode.plugin.j2735;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Mocked;

//import mockit.integration.junit4.JMockit;

import mockit.Tested;
//@RunWith(JMockit.class)
public class J2735BsmTest {
   @Mocked
   J2735Bsm b;
   @Test
   public void testGettersAndSetters() {
      J2735BsmCoreData coreData = new J2735BsmCoreData();
      b.setCoreData(coreData);
      assertEquals(coreData,b.getCoreData());
      List<J2735BsmPart2Content> partII = new ArrayList<>();
      b.setPartII(partII);
      assertEquals(partII,b.getPartII());
   }
}
