package lu.luz.jzopfli_mvn;

/*
 * #%L
 * JZopfli Maven
 * %%
 * Copyright (C) 2015 Luc Cappellaro
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


public class ZipOptions {
	private boolean keepDirectories;
	private boolean keepExtra;
	private boolean keepComment;
	private boolean keepNestedZips;
	public ZipOptions() {
	}

	public ZipOptions(boolean keepDirectories, boolean keepExtra, boolean keepComment, boolean keepNestedZips) {
		this.keepDirectories = keepDirectories;
		this.keepExtra = keepExtra;
		this.keepComment = keepComment;
		this.keepNestedZips = keepNestedZips;
	}

	public boolean isKeepDirectories() {
		return keepDirectories;
	}

	public void setKeepDirectories(boolean keepDirectories) {
		this.keepDirectories = keepDirectories;
	}

	public boolean isKeepExtra() {
		return keepExtra;
	}

	public void setKeepExtra(boolean keepExtra) {
		this.keepExtra = keepExtra;
	}

	public boolean isKeepComment() {
		return keepComment;
	}

	public void setKeepComment(boolean keepComment) {
		this.keepComment = keepComment;
	}

	public boolean isKeepNestedZips() {
		return keepNestedZips;
	}

	public void setKeepNestedZips(boolean keepNestedZips) {
		this.keepNestedZips = keepNestedZips;
	}
}
