/*******************************************************************************
 * Copyright 2014 Soreepeong
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.soreepeong.darknova.ui.dragaction;

/**
 * Drag Initiator's Callbacks
 * 
 * @author Soreepeong
 * 
 */
public interface DragInitiatorCallbacks{

	/**
	 * Called when drag is about to begin.
	 * 
	 * @param dragInitiator
	 *            DragInitiator associated with this event.
	 */
	void onDragStart(DragInitiator dragInitiator);

	/**
	 * Called when drag just end.
	 * 
	 * @param dragInitiator
	 *            DragInitiator associated with this event.
	 */
	void onDragEnd(DragInitiator dragInitiator);

	void onDragPrepare(DragInitiator dragInitiator);
}
