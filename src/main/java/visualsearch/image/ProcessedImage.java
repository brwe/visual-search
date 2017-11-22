/*
 * Copyright 2017 a2tirb
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

package visualsearch.image;

import java.util.HashMap;
import java.util.Map;

public class ProcessedImage {
    public final int receivedBytes;
    public final String imageUrl;
    public int numPixels = 0;
    public final Map<String, Boolean> dHash;


    protected ProcessedImage(int receivedBytes, String imageUrl, int numPixels, Map<String,Boolean> dHash) {
        this.receivedBytes = receivedBytes;
        this.imageUrl = imageUrl;
        this.numPixels = numPixels;
        this.dHash = dHash;
    }

    public static class Builder {
        int capacity = 0;
        String imageUrl;
        int numPixels;
        Map<String, Boolean> dHash = new HashMap();

        public ProcessedImage build() {
            assert (imageUrl != null);
            return new ProcessedImage(capacity, imageUrl, numPixels, dHash);
        }

        public Builder capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Builder numPixels(int numPixels) {
            this.numPixels = numPixels;
            return this;
        }

        public void dHash(boolean[] hashBits) {
            for (int i = 0; i < hashBits.length; i++) {
                dHash.put("dh_" + i, hashBits[i]);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
