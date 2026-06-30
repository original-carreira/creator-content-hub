/*
 * TimelineRenderer
 *
 * RESPONSIBILITIES:
 * - timeline rendering
 * - timeline visual composition
 *
 * IMPORTANT:
 * - must NOT control HTML5 Video
 * - must NOT perform seek operations
 * - must NOT access WorkspaceState
 * - must NOT manipulate DOM after rendering
 * - must NOT access backend
 *
 * DOM CONTRACT:
 * - timeline
 * - timelineHeader
 * - timelineViewport
 * - timelineTrack
 * - timelineCurrentPositionLayer
 * - timelineSelectionLayer
 * - timelineMarkerLayer
 * - timelineOverlayLayer
 * - timelineFooter
 *
 * timelineViewModel
 *
 * Current Contract:
 * {
 *     duration,
 *     currentTime,
 *     selections,
 *     activeSelectionId
 * }
 *
 * Future Extensions:
 * - markers
 * - overlays
 *
 * NOTA PARA FUNDAÇÕES FUTURAS
 *
 * O TimelineTrack é intencionalmente o sistema de
 * coordenadas de todas as camadas da Timeline.
 *
 * O TimelineViewport é responsável apenas pelo
 * layout do componente.
 *
 * O TimelineTrack é responsável pelo espaço de
 * coordenadas utilizado pelas camadas operacionais.
 *
 * Fundações futuras (Zoom, Waveform e
 * Virtualização) poderão introduzir um
 * TimelineCanvas entre TimelineViewport e
 * TimelineTrack sem alterar o contrato do
 * TimelineTrack nem das suas camadas.
 */



function renderTimeline(
    timelineViewModel
) {

    return `
        <div
            id="timeline"
            class="timeline"
        >

            <div
                id="timelineHeader"
                class="timeline-header"
            >

                <div
                    id="timelineTitle"
                    class="timeline-title"
                >
                    Timeline
                </div>

            </div>

            <div
                id="timelineViewport"
                class="timeline-viewport"
            >
                <!--
                    FUNDAÇÃO FUTURA

                    TimelineCanvas
                    
                    Quando a Timeline evoluir para:
                    
                    - Zoom
                    - Rolagem horizontal
                    - Waveform
                    - Renderização virtual
                    
                    as camadas abaixo poderão ser movidas para
                    TimelineCanvas sem alterar o contrato do
                    TimelineTrack nem das camadas existentes.
                -->
                <div 
                    id="timelineTrack" 
                    class="timeline-track"
                >
                    <div
                        id="timelineTrackBar"
                        class="timeline-track-bar"
                    ></div>
                              
                    <!-- Current Position Layer -->
                    <div
                        id="timelineCurrentPositionLayer"
                        class="timeline-current-position-layer"
                    >
                    
                        <div
                            id="timelineCurrentPositionIndicator"
                            class="timeline-current-position-indicator"
                        ></div>
                        
                        <div
                            id="timelineCurrentPositionLabel"
                            class="timeline-current-position-label"
                        >
                            00:00:00
                        </div>
                    </div>
                
                    <!-- Selection Layer -->
                    <div
                        id="timelineSelectionLayer"
                        class="timeline-selection-layer"
                    ></div>
    
                    <!-- Marker Layer -->               
                    <div
                        id="timelineMarkerLayer"
                        class="timeline-marker-layer"
                    ></div>
                    
                    <!-- Overlay Layer -->
                    <div
                        id="timelineOverlayLayer"
                        class="timeline-overlay-layer"
                    ></div> 
                </div>
            </div>

            <div
                id="timelineFooter"
                class="timeline-footer"
            >
            
                <div
                    id="timelineTimeScale"
                    class="timeline-time-scale"
                >
            
                    <span
                        id="timelineStartTime"
                        class="timeline-start-time"
                    >
                        00:00:00
                    </span>
                    
                    <span
                        id="timelineHoverTime"
                        class="timeline-hover-time"
                    >
                    </span>
            
                    <span
                        id="timelineEndTime"
                        class="timeline-end-time"
                    >
                        ${timelineViewModel.durationText}
                    </span>
            
                </div>
            
            </div>

        </div>
    `;
}

window.TimelineRenderer = {

    renderTimeline
};