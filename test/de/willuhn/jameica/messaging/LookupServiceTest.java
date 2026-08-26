/**********************************************************************
 *
 * Copyright (c) 2004 Olaf Willuhn
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * Jameica License.  Please consult the file "LICENSE" for details.
 *
 **********************************************************************/

package de.willuhn.jameica.messaging;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the multicast lookup policy.
 */
public class LookupServiceTest
{
  /**
   * Plaintext TCP services must not be selected through unauthenticated discovery.
   */
  @Test
  public void rejectPlaintextTcpLookup()
  {
    Assert.assertNull(LookupService.lookup("tcp:de.willuhn.jameica.messaging.Plugin.connector.tcp"));
  }
}
