import { Directive, EventEmitter, Input, NgModule, Output } from '@angular/core';

@Directive({
    selector: '[ngbCollapse]',
})
class NgbCollapseMockDirective {
    @Input() animation: any;
    private _isCollapsed = false;
    @Input('ngbCollapse')
    set collapsed(isCollapsed: boolean) {
        if (this._isCollapsed !== isCollapsed) {
            this._isCollapsed = isCollapsed;
        }
    }

    @Output() ngbCollapseChange = new EventEmitter<boolean>();
    @Input() horizontal: boolean;
    @Output() shown = new EventEmitter<void>();
    @Output() hidden = new EventEmitter<void>();
    toggle() {}
}

@NgModule({
    imports: [NgbCollapseMockDirective],
})
export class NgbCollapseMocksModule {}
