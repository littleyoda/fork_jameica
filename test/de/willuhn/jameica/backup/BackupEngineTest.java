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
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests fuer die Validierung von Backup-Dateien.
 */
public class BackupEngineTest
{
  /** Testverzeichnis. */
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /**
   * Legitime Eintraege innerhalb des Zielverzeichnisses werden akzeptiert.
   * @throws Exception
   */
  @Test
  public void testValidEntries() throws Exception
  {
    File target = tmp.newFolder("target");
    File file = this.createZip("valid.zip","cfg/test.properties","data/test.txt");
    try (ZipFile zip = new ZipFile(file))
    {
      BackupEngine.validateRestore(zip,target);
    }
  }

  /**
   * Ein Pfad ausserhalb des Zielverzeichnisses wird abgelehnt.
   * @throws Exception
   */
  @Test
  public void testPathTraversal() throws Exception
  {
    File target = tmp.newFolder("target");
    this.assertInvalid(target,"invalid.zip","../outside.txt");
  }

  /**
   * Auch verschachtelte Traversal-Pfade werden abgelehnt.
   * @throws Exception
   */
  @Test
  public void testNestedPathTraversal() throws Exception
  {
    File target = tmp.newFolder("nested-target");
    this.assertInvalid(target,"nested-invalid.zip","nested/../../outside.txt");
    this.assertInvalid(target,"root-invalid.zip","nested/..");
  }

  /**
   * Absolute Pfade werden abgelehnt.
   * @throws Exception
   */
  @Test
  public void testAbsolutePath() throws Exception
  {
    File target = tmp.newFolder("absolute-target");
    File outside = new File(tmp.getRoot(),"absolute-outside.txt");
    this.assertInvalid(target,"absolute-invalid.zip",outside.getAbsolutePath());
  }

  /**
   * Windows-Laufwerks- und UNC-Pfade werden plattformunabhaengig abgelehnt.
   * @throws Exception
   */
  @Test
  public void testWindowsAbsolutePaths() throws Exception
  {
    File target = tmp.newFolder("windows-target");
    this.assertInvalid(target,"drive-invalid.zip","C:\\outside.txt");
    this.assertInvalid(target,"unc-invalid.zip","\\\\server\\share\\outside.txt");
  }

  /**
   * Bestehende Symlinks duerfen nicht aus dem Zielverzeichnis fuehren.
   * @throws Exception
   */
  @Test
  public void testSymlinkTraversal() throws Exception
  {
    File target = tmp.newFolder("symlink-target");
    File outside = tmp.newFolder("symlink-outside");
    try
    {
      Files.createSymbolicLink(new File(target,"link").toPath(),outside.toPath());
    }
    catch (IOException | UnsupportedOperationException e)
    {
      Assume.assumeNoException(e);
    }
    this.assertInvalid(target,"symlink-invalid.zip","link/outside.txt");
  }

  /**
   * Prueft, dass ein einzelner ungueltiger Eintrag abgelehnt wird.
   * @param target Zielverzeichnis.
   * @param zipName Dateiname des Archivs.
   * @param entryName Name des ZIP-Eintrags.
   * @throws Exception
   */
  private void assertInvalid(File target, String zipName, String entryName) throws Exception
  {
    File file = this.createZip(zipName,entryName);
    try (ZipFile zip = new ZipFile(file))
    {
      try
      {
        BackupEngine.validateRestore(zip,target);
        Assert.fail("ZIP entry was accepted: " + entryName);
      }
      catch (IOException expected)
      {
        // expected
      }
    }
  }

  /**
   * Erzeugt eine ZIP-Datei fuer den Test.
   * @param name Dateiname.
   * @param entries Eintraege.
   * @return die ZIP-Datei.
   * @throws Exception
   */
  private File createZip(String name, String... entries) throws Exception
  {
    File file = new File(tmp.getRoot(),name);
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file)))
    {
      for (String nameOfEntry:entries)
      {
        zip.putNextEntry(new ZipEntry(nameOfEntry));
        zip.write("test".getBytes("ISO-8859-1"));
        zip.closeEntry();
      }
    }
    return file;
  }
}
