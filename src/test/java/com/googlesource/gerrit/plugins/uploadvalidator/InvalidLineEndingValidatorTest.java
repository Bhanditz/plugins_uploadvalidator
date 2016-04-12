// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.uploadvalidator;

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.plugins.uploadvalidator.TestUtils.EMPTY_PLUGIN_CONFIG;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.server.git.validators.CommitValidationMessage;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InvalidLineEndingValidatorTest extends ValidatorTestCase {
  private RevCommit makeCommit()
      throws NoFilepatternException, IOException, GitAPIException {
    Map<File, byte[]> files = new HashMap<>();
    // invalid line endings
    String content = "Testline1\r\n"
        + "Testline2\n"
        + "Testline3\r\n"
        + "Testline4";
    files.put(new File(repo.getDirectory().getParent(), "foo.txt"),
        content.getBytes(StandardCharsets.UTF_8));

    // valid line endings
    content = "Testline1\n"
        + "Testline2\n"
        + "Testline3\n"
        + "Testline4";
    files.put(new File(repo.getDirectory().getParent(), "bar.txt"),
        content.getBytes(StandardCharsets.UTF_8));
    return TestUtils.makeCommit(repo, "Commit with test files.", files);
  }

  @Test
  public void testCarriageReturn() throws Exception {
    RevCommit c = makeCommit();
    List<CommitValidationMessage> m = InvalidLineEndingValidator
        .performValidation(repo, c, Sets.newHashSet(new String[] {""}));
    assertThat(TestUtils.transformMessages(m))
        .containsExactlyElementsIn(ImmutableSet.of(
            "ERROR: found carriage return (CR) character in file: foo.txt"));
  }

  private RevCommit makeCommitWithPseudoBinaries()
      throws NoFilepatternException, IOException, GitAPIException {
    Map<File, byte[]> files = new HashMap<>();
    // invalid line endings
    String content = "Testline1\r\n"
        + "Testline2\n"
        + "Testline3\r\n"
        + "Testline4";
    files.put(new File(repo.getDirectory().getParent(), "foo.jpeg"),
        content.getBytes(StandardCharsets.UTF_8));

    content = "Testline1\r\n"
        + "Testline2\r\n"
        + "Testline3\r\n"
        + "Testline4";
    files.put(new File(repo.getDirectory().getParent(), "foo.iso"),
        content.getBytes(StandardCharsets.UTF_8));

    // valid line endings
    content = "Testline1\n"
        + "Testline2\n"
        + "Testline3\n"
        + "Testline4";
    files.put(new File(repo.getDirectory().getParent(), "bar.txt"),
        content.getBytes(StandardCharsets.UTF_8));
    return TestUtils.makeCommit(repo, "Commit with test files.", files);
  }

  @Test
  public void testCarriageReturnWithBinaries() throws Exception {
    RevCommit c = makeCommitWithPseudoBinaries();
    List<CommitValidationMessage> m = InvalidLineEndingValidator
        .performValidation(repo, c, Sets.newHashSet(new String[] {""}));
    Set<String> expected = ImmutableSet.of(
        "ERROR: found carriage return (CR) character in file: foo.jpeg",
        "ERROR: found carriage return (CR) character in file: foo.iso");
    assertThat(TestUtils.transformMessages(m))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void testCarriageReturnIgnoringBinaries() throws Exception {
    RevCommit c = makeCommitWithPseudoBinaries();
    Set<String> ignoreFiles = Sets.newHashSet(new String[]{"iso", "jpeg"});
    List<CommitValidationMessage> m =
        InvalidLineEndingValidator.performValidation(repo, c, ignoreFiles);
    assertThat(m).isEmpty();
  }

  @Test
  public void validatorInactiveWhenConfigEmpty() {
    assertThat(InvalidLineEndingValidator.isActive(EMPTY_PLUGIN_CONFIG)).isFalse();
  }
}
