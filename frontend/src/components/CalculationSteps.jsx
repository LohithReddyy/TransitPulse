/**
 * Renders a list of {step, description, calculation} objects.
 *
 * Showing the arithmetic is the point: an operator should be able to check
 * every propagation figure by hand.
 */
export default function CalculationSteps({ steps }) {
  if (!steps || steps.length === 0) return null;

  return (
    <div className="calc-steps">
      {steps.map((step, index) => (
        <div className="calc-step" key={step.step ?? index}>
          <div className="calc-step-number">{step.step ?? index + 1}</div>
          <div className="calc-step-body">
            <div className="calc-step-desc">{step.description}</div>
            {step.calculation && (
              <div className="calc-formula">{step.calculation}</div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
