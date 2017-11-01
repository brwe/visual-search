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

public class ProcessedImage {
    public final int receivedBytes;
    public final String imageUrl;


    protected ProcessedImage(int receivedBytes, String imageUrl) {
        this.receivedBytes = receivedBytes;
        this.imageUrl = imageUrl;
    }

    public static class Builder {
        int capacity = 0;
        String imageUrl;

        public ProcessedImage build() {
            assert (imageUrl != null);
            return new ProcessedImage(capacity, imageUrl);
        }

        public Builder capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
