/**********************************************************************
 *
 * Copyright (c) 2004 Olaf Willuhn
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * Jameica License.  Please consult the file "LICENSE" for details.
 *
 **********************************************************************/

package de.willuhn.jameica.services;

import java.net.InetSocketAddress;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the explicit archive server configuration.
 */
public class ArchiveServiceTest
{
  /**
   * No remote endpoint exists without an explicit configuration.
   */
  @Test
  public void noEndpointWithoutConfiguration()
  {
    Assert.assertNull(ArchiveService.parseArchiveServer(null));
    Assert.assertNull(ArchiveService.parseArchiveServer("  "));
  }

  /**
   * An explicitly configured endpoint is accepted without resolving it.
   */
  @Test
  public void parseExplicitArchiveServer()
  {
    InetSocketAddress endpoint = ArchiveService.parseArchiveServer(" archive.example : 9099 ");
    Assert.assertTrue(endpoint.isUnresolved());
    Assert.assertEquals("archive.example",endpoint.getHostString());
    Assert.assertEquals(9099,endpoint.getPort());

    endpoint = ArchiveService.parseArchiveServer("[::1]:9099");
    Assert.assertEquals("::1",endpoint.getHostString());
    Assert.assertEquals(9099,endpoint.getPort());
  }

  /**
   * Malformed endpoints fail closed.
   */
  @Test
  public void rejectInvalidArchiveServers()
  {
    String[] invalid = new String[]{"archive.example",":9099","archive.example:","archive.example:0","archive.example:65536","archive.example:not-a-port","[]:9099"};
    for (String value: invalid)
    {
      try
      {
        ArchiveService.parseArchiveServer(value);
        Assert.fail("accepted invalid archive server: " + value);
      }
      catch (IllegalArgumentException expected)
      {
        // expected
      }
    }
  }
}
