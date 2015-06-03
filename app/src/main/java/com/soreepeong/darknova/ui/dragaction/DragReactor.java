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
 * Callback functions for drag reactors.
 * 
 * @author Soreepeong
 * 
 */
public interface DragReactor{

	/**
	 * Dragging has just begun and reactor must be ready to accept drags
	 */
	public void onDragReactorReady(DragReactor reactor, DragInitiator dragFrom);

	/**
	 * Dragging is about to stop and reactor must stop accepting drags
	 */
	public void onDragReactorStop(DragReactor reactor);

	/**
	 * Get DragReactor's Action Type.
	 * 
	 * @return DragReactor's Action Type.
	 */
	public int getDragReactorActionType(DragReactor reactor);
}
