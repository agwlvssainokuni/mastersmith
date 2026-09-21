/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mastersmith.permission.rbacio;

import com.mastersmith.common.configio.ApplyResult;
import com.mastersmith.common.configio.ImportValidationError;
import com.mastersmith.permission.dto.RbacExport;
import com.mastersmith.permission.dto.RbacImportSet;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * permission-engineの、RBAC設定の書き出し・取り込みの検証・取り込みの反映を、まとめて{@code PermissionEngineApiImpl}に渡す窓口(C10の追補)。
 */
@Component
public class RbacTransfer {

  private final RbacExporter exporter;
  private final RbacImportValidator validator;
  private final RbacImporter importer;

  public RbacTransfer(RbacExporter exporter, RbacImportValidator validator, RbacImporter importer) {
    this.exporter = exporter;
    this.validator = validator;
    this.importer = importer;
  }

  public RbacExport export() {
    return exporter.export();
  }

  public List<ImportValidationError> validate(
      RbacImportSet set, String actorRoleId, boolean bootstrapAtStart) {
    return validator.validate(set, actorRoleId, bootstrapAtStart);
  }

  public ApplyResult apply(RbacImportSet set, String actorRoleId) {
    return importer.apply(set, actorRoleId);
  }
}
