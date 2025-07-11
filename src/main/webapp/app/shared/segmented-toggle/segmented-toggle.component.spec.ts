import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SegmentedToggleComponent } from 'app/shared/segmented-toggle/segmented-toggle.component';

describe('SegmentedToggleComponent', () => {
    let component: SegmentedToggleComponent<number>;
    let fixture: ComponentFixture<SegmentedToggleComponent<number>>;

    const mockOptions = [
        { label: 'Option 1', value: 1 },
        { label: 'Option 2', value: 2 },
        { label: 'Option 3', value: 3 },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SegmentedToggleComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(SegmentedToggleComponent<number>);
        component = fixture.componentInstance;
        component.options = mockOptions;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.options).toEqual(mockOptions);
    });

    it('should bind selected value correctly', () => {
        component.selected = 2;
        fixture.detectChanges();
        expect(component.selected).toBe(2);
    });

    it('should emit selectedChange event when an option is selected', () => {
        const selectedValue = 1;
        const spy = jest.spyOn(component.selectedChange, 'emit');

        component.select(selectedValue);

        expect(spy).toHaveBeenCalledWith(selectedValue);
        expect(component.selected).toBe(selectedValue);
    });

    it('should handle empty options array', () => {
        component.options = [];
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const options = compiled.querySelectorAll('.btn');
        expect(options).toHaveLength(0);
    });

    it('should render all options correctly', () => {
        component.options = mockOptions;
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const options = compiled.querySelectorAll('.btn');
        expect(options).toHaveLength(mockOptions.length);

        options.forEach((option: HTMLElement, index: number) => {
            expect(option.textContent?.trim()).toBe(mockOptions[index].label);
        });
    });

    it('should apply selected class to the active option', () => {
        component.options = mockOptions;
        component.selected = 2;
        fixture.detectChanges();

        const compiled = fixture.nativeElement;
        const selectedOption = compiled.querySelector('.btn-primary.selected');
        expect(selectedOption).toBeTruthy();
        expect(selectedOption.textContent.trim()).toBe('Option 2');
    });
});
