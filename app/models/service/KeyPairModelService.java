/*
 * Copyright (c) 2014-2015 University of Ulm
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package models.service;

import models.CloudCredential;
import models.KeyPair;

import java.util.Optional;

/**
 * Created by daniel on 28.08.15.
 */
public interface KeyPairModelService extends ModelService<KeyPair> {

    /**
     * Searches the database for an {@link Optional} keypair for the given cloud credential.
     *
     * @param cloudCredential the cloudCredential for the keypair (mandatory)
     * @return the optional keypair.
     * @throws NullPointerException if cloudCredential is null.
     */
    Optional<KeyPair> getKeyPair(CloudCredential cloudCredential);

}
