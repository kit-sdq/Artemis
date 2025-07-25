import { ComponentFixture, TestBed } from '@angular/core/testing';
import { runOnPushChangeDetection } from 'test/helpers/on-push-change-detection.helper';
import { VerticalProgressBarComponent } from 'app/tutorialgroup/shared/vertical-progress-bar/vertical-progress-bar.component';

describe('VerticalProgressBarComponent', () => {
    let component: VerticalProgressBarComponent;
    let fixture: ComponentFixture<VerticalProgressBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();

        fixture = TestBed.createComponent(VerticalProgressBarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should keep borders in range [0,100]', () => {
        component.lowerBorder = -20;
        component.upperBorder = 120;
        fixture.detectChanges();
        runOnPushChangeDetection(fixture);
        expect(component.lowerBorderInternal).toBe(0);
        expect(component.upperBorderInternal).toBe(100);
    });

    it('should change color', () => {
        component.lowerBorder = 20;
        component.upperBorder = 40;
        fixture.componentRef.setInput('lowerColor', 'red');
        fixture.componentRef.setInput('intermediateColor', 'orange');
        fixture.componentRef.setInput('upperColor', 'green');
        component.animateFilling = false;
        component.heightInPixels = 100;
        component.widthInPixels = 100;
        // lower area
        component.fillLevelInPercent = 10;
        fixture.detectChanges();
        runOnPushChangeDetection(fixture);
        expect(component.fillColorCSS).toBe('red');
        component.fillLevelInPercent = 20;
        runOnPushChangeDetection(fixture);
        expect(component.fillColorCSS).toBe('red');
        // intermediate area
        component.fillLevelInPercent = 21;
        runOnPushChangeDetection(fixture);
        expect(component.fillColorCSS).toBe('orange');
        component.fillLevelInPercent = 30;
        runOnPushChangeDetection(fixture);
        expect(component.fillColorCSS).toBe('orange');
        component.fillLevelInPercent = 39;
        runOnPushChangeDetection(fixture);
        expect(component.fillColorCSS).toBe('orange');
        // upper area
        component.fillLevelInPercent = 40;
        runOnPushChangeDetection(fixture);
        expect(component.fillColorCSS).toBe('green');
        component.fillLevelInPercent = 50;
        runOnPushChangeDetection(fixture);
        expect(component.fillColorCSS).toBe('green');
    });
});
