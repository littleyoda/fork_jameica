/**********************************************************************
 *
 * Copyright (c) 2026 Olaf Willuhn
 * All rights reserved.
 *
 * This software is copyrighted work licensed under the terms of the
 * Jameica License.  Please consult the file "LICENSE" for details.
 *
 **********************************************************************/

package de.willuhn.jameica.backup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.willuhn.util.ApplicationException;

/**
 * Tests restore-path and active-content validation of backup files.
 */
public class BackupEngineTest
{
  /** Test directory. */
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  /**
   * Ordinary entries pass both validators without closing the shared ZIP.
   */
  @Test
  public void acceptValidEntriesAndKeepZipOpen() throws Exception
  {
    File target = folder.newFolder("valid-target");
    File backup = createBackup("cfg/test.properties","value=data\n","data/test.txt","data");
    try (ZipFile zip = new ZipFile(backup))
    {
      BackupEngine.validateRestore(zip,target);
      BackupEngine.validateActiveContent(zip,target);
      Assert.assertNotNull(zip.getEntry("data/test.txt"));
    }
  }

  /** Relative traversal must leave no entry outside the target. */
  @Test
  public void rejectPathTraversal() throws Exception
  {
    File target = folder.newFolder("traversal-target");
    assertInvalidPath(target,"../outside.txt");
    assertInvalidPath(target,"nested/../../outside.txt");
    assertInvalidPath(target,"nested/..");
  }

  /** POSIX, drive-letter and UNC absolute paths are all invalid. */
  @Test
  public void rejectAbsolutePaths() throws Exception
  {
    File target = folder.newFolder("absolute-target");
    assertInvalidPath(target,new File(folder.getRoot(),"outside.txt").getAbsolutePath());
    assertInvalidPath(target,"C:\\outside.txt");
    assertInvalidPath(target,"\\\\server\\share\\outside.txt");
  }

  /** Existing links must not lead outside the restore target. */
  @Test
  public void rejectSymlinkTraversal() throws Exception
  {
    File target = folder.newFolder("symlink-target");
    File outside = folder.newFolder("symlink-outside");
    try
    {
      Files.createSymbolicLink(new File(target,"link").toPath(),outside.toPath());
    }
    catch (IOException | UnsupportedOperationException e)
    {
      Assume.assumeNoException(e);
    }
    assertInvalidPath(target,"link/outside.txt");
  }

  /** NTFS alternate-stream syntax is not a portable backup entry name. */
  @Test
  public void rejectAlternateDataStreamName() throws Exception
  {
    File target = folder.newFolder("ads-target");
    assertInvalidPath(target,"cfg/de.willuhn.jameica.services.ScriptingService.properties::$DATA");
  }

  /** Windows aliases must not normalize a different component into active data. */
  @Test
  public void rejectTrailingDotAndSpaceComponents() throws Exception
  {
    File target = folder.newFolder("windows-alias-target");
    assertInvalidPath(target,"plugins./untrusted/plugin.xml");
    assertInvalidPath(target,"plugins /untrusted/plugin.xml");
  }

  /** Ordinary settings, data and unregistered scripts remain restorable. */
  @Test
  public void acceptDataOnlyBackup() throws Exception
  {
    File target = folder.newFolder("data-target");
    File backup = createBackup(
        "cfg/de.willuhn.jameica.services.ScriptingService.properties","migrated=20260827\nscript.encoding=UTF-8\n",
        "cfg/de.willuhn.jameica.services.UpdateService.properties","update.check=true\nupdate.install=false\n",
        "cfg/de.willuhn.jameica.system.Config.properties","jameica.system.archive.server=  \n",
        "scripts/report.js","print('registered manually after restore');\n",
        "data/plugins/plugin.xml","<plugin/>",
        "plugins-old/readme.txt","ordinary data\n",
        "lost+found/orphan","ordinary data\n");
    try (ZipFile zip = new ZipFile(backup))
    {
      BackupEngine.validateRestore(zip,target);
      BackupEngine.validateActiveContent(zip,target);
    }
  }

  /** Current scripting settings must not activate code during startup. */
  @Test(expected=ApplicationException.class)
  public void rejectCurrentScriptRegistration() throws Exception
  {
    validateActive("cfg/de.willuhn.jameica.services.ScriptingService.properties","scripts.0=scripts/restore.js\n");
  }

  /** Legacy scripting settings are migrated and therefore equally active. */
  @Test(expected=ApplicationException.class)
  public void rejectLegacyScriptRegistration() throws Exception
  {
    validateActive("cfg/de.willuhn.jameica.scripting.Plugin.properties","scripts.0=scripts/restore.js\n");
  }

  /** Java-properties escapes must not bypass the key check. */
  @Test(expected=ApplicationException.class)
  public void rejectEscapedScriptRegistration() throws Exception
  {
    validateActive("cfg/de.willuhn.jameica.services.ScriptingService.properties","scr\\u0069pts.0=scripts/restore.js\n");
  }

  /** Redundant path segments must not bypass protected-path checks. */
  @Test(expected=ApplicationException.class)
  public void rejectNormalizedScriptSettingsPath() throws Exception
  {
    validateActive("cfg/old/../de.willuhn.jameica.services.ScriptingService.properties","scripts.0=scripts/restore.js\n");
  }

  /** Plugin files contain active code and are not valid backup data. */
  @Test(expected=ApplicationException.class)
  public void rejectRestoredPlugin() throws Exception
  {
    validateActive("plugins/untrusted/plugin.xml","<plugin/>");
  }

  /** Matching is case-insensitive and follows canonical path resolution. */
  @Test(expected=ApplicationException.class)
  public void rejectNormalizedPluginDirectory() throws Exception
  {
    validateActive("data/../PlUgInS/untrusted/plugin.xml","<plugin/>");
  }

  /** Backslash-separated update paths follow the same policy. */
  @Test(expected=ApplicationException.class)
  public void rejectRestoredUpdate() throws Exception
  {
    validateActive("UPDATES\\update.jar","content");
  }

  /** A restored config must not activate a separate plugin directory. */
  @Test(expected=ApplicationException.class)
  public void rejectConfiguredPluginDirectory() throws Exception
  {
    validateActive("cfg/de.willuhn.jameica.system.Config.properties","jameica.plugin.dir.0=/tmp/untrusted\n");
  }

  /** A restored config must not silently reactivate a plaintext archive endpoint. */
  @Test(expected=ApplicationException.class)
  public void rejectConfiguredArchiveServer() throws Exception
  {
    validateActive("cfg/de.willuhn.jameica.system.Config.properties","jameica.system.archive.server=archive.example:8080\n");
  }

  /** Automatic plugin installation is active code delivery, including escaped values. */
  @Test(expected=ApplicationException.class)
  public void rejectAutomaticPluginInstallation() throws Exception
  {
    validateActive("cfg/de.willuhn.jameica.services.UpdateService.properties","update.check=true\nupdate.install=tr\\u0075e\nlastrun=0\n");
  }

  /** Canonical in-root aliases into the live plugin directory remain active. */
  @Test
  public void rejectSymlinkAliasIntoPluginDirectory() throws Exception
  {
    File target = folder.newFolder("active-alias-target");
    File plugins = new File(target,"plugins");
    Assert.assertTrue(plugins.mkdir());
    try
    {
      Files.createSymbolicLink(new File(target,"alias").toPath(),plugins.toPath());
    }
    catch (IOException | UnsupportedOperationException e)
    {
      Assume.assumeNoException(e);
    }

    File backup = createBackup("alias/untrusted/plugin.xml","<plugin/>");
    try (ZipFile zip = new ZipFile(backup))
    {
      BackupEngine.validateRestore(zip,target);
      try
      {
        BackupEngine.validateActiveContent(zip,target);
        Assert.fail("canonical plugin alias was accepted");
      }
      catch (ApplicationException expected)
      {
        // expected
      }
    }
  }

  private void validateActive(String name, String content) throws Exception
  {
    File target = folder.newFolder("active-target-" + System.nanoTime());
    try (ZipFile zip = new ZipFile(createBackup(name,content)))
    {
      BackupEngine.validateActiveContent(zip,target);
    }
  }

  private void assertInvalidPath(File target, String name) throws Exception
  {
    try (ZipFile zip = new ZipFile(createBackup(name,"test")))
    {
      try
      {
        BackupEngine.validateRestore(zip,target);
        Assert.fail("ZIP entry was accepted: " + name);
      }
      catch (IOException expected)
      {
        // expected
      }
    }
  }

  private File createBackup(String... entries) throws Exception
  {
    File file = folder.newFile("backup-" + System.nanoTime() + ".zip");
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file)))
    {
      for (int i=0;i<entries.length;i+=2)
      {
        zip.putNextEntry(new ZipEntry(entries[i]));
        zip.write(entries[i+1].getBytes(StandardCharsets.ISO_8859_1));
        zip.closeEntry();
      }
    }
    return file;
  }
}
